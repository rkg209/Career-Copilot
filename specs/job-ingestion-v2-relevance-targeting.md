# Spec: Job Ingestion v2 — Relevance-Targeted Multi-Source Scraping

## Header

| Field | Value |
|---|---|
| Phase | Phase 1.5 — Job Ingestion v2 (Relevance-Targeted Multi-Source Scraping) |
| Status | APPROVED |
| Version | 1.0 |
| Author | Rahul |
| Last Updated | 2026-07-06 |

---

## Goal

The current ingestion pipeline dumps *every* posting from a small, hardcoded set of generic boards
(RemoteOK's global feed + 5 Greenhouse companies), with no relationship to what the candidate is
actually looking for. The result is a stale, noise-heavy "static list" — designers, recruiters, and
sales roles from a handful of companies, refreshed every 6 hours with no new variety. This phase
makes ingestion **target-driven**: a structured YAML config of roles/title-keywords, locations, and
experience levels filters postings at ingestion time so only relevant jobs are stored; broadens the
source pool (more Greenhouse/Lever boards including quant/HFT and Indian product companies, plus a
new keyless **Ashby** adapter); deterministically extracts skills from descriptions (reusing the
scoring slice's `SkillAliasMap`, no LLM); and adds a freshness lifecycle so stale postings are
deactivated and the browse view reads as live. The user-facing outcome: the job portal shows a
dense, fresh, on-target list of AI/ML, SDE, and quant roles instead of a static generic dump.

---

## Scope

### In Scope

1. **`JobRelevanceFilter`** — a config-driven, deterministic filter applied inside
   `JobIngestionService.ingestFrom()` *before* upsert. A `NormalizedJob` is kept only if its
   title/full-text matches a configured target role keyword **and** (if any location terms are
   configured) its location matches a configured location term or it is `remote`. Off-target
   postings are dropped (never persisted) and counted.
2. **`jobs.targets` YAML config block** — structured targeting derived from the design doc: role
   groups → title keywords, location terms, experience-level terms, and a skills allow-list hint.
   Fully overridable by env var; ships with sensible AI/ML + SDE + quant + India/Remote defaults.
3. **`SkillExtractor`** — deterministic skill extraction from `NormalizedJob.fullText`/`title`,
   **reusing the existing `com.careercopilot.scoring.SkillAliasMap`** (alias-normalized 1–3-gram
   matching). Populates `NormalizedJob.skills` for adapters that return none (Greenhouse, Lever,
   Ashby) and enriches those that do (RemoteOK). No LLM, no new cost.
4. **Expanded source breadth via config** — move Greenhouse/Lever/Ashby board tokens into config
   with a broader curated default set (quant/HFT boards, Indian product companies), and enable
   Lever + Ashby by default alongside RemoteOK + Greenhouse.
5. **`AshbyJobSource`** — new keyless `JobSourceAdapter` for boards hosted on Ashby
   (`https://api.ashbyhq.com/posting-api/job-board/{token}`), mapping the public JSON into
   `NormalizedJob`. New `JobSource.ASHBY` enum value (additive migration).
6. **Freshness lifecycle** — a scheduled `deactivateStale()` sweep marks postings whose
   `posted_at` (or `ingested_at` when `posted_at` is null) is older than a configurable cutoff as
   `is_active = false`, so they drop out of browse/search. Browse already defaults to
   `postedAt,desc`; this spec confirms/keeps that default. Re-ingested postings are re-activated.

### Out of Scope

- Any change to `auth`, `profile`, `tailoring`, `scoring`, or `intel` slices (the `SkillAliasMap`
  is **read-only reused**, not modified).
- HTML scraping, headless browsers, Playwright, or any JavaScript rendering. Keyless public JSON
  APIs only.
- Paid/keyed aggregators. Adzuna stays built-but-disabled; LinkedIn/Naukri/Indeed/Instahyre are
  **not** added (they require auth, violate ToS for scraping, or need paid access).
- A `companies` table, a `skills` join table, or a URL work-queue table. The existing single
  `job_postings` table + `text[] skills` column + `(source, external_id)` dedup key are retained.
- LLM-based skill/metadata extraction, salary parsing from free text, deadline extraction, and
  company/location canonicalization beyond trimming (deferred; noted as future work).
- Frontend changes beyond what already exists (the browse/search/detail pages already render
  `skills`, `location`, `postedAt`).

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-1.5.1 | Ingestion filters each fetched posting against a configured set of target role/title keywords; non-matching postings are not persisted. |
| FR-1.5.2 | Ingestion filters by configured location terms (matching location string or `remote`); when no location terms are configured, no location filter is applied. |
| FR-1.5.3 | `skills[]` is populated deterministically from title/description via `SkillAliasMap` for every ingested posting that has no source-provided skills. |
| FR-1.5.4 | Target roles, locations, experience terms, and source board tokens are all config-driven and env-overridable; no company/keyword list is hardcoded in Java. |
| FR-1.5.5 | A new Ashby adapter ingests postings from configured Ashby board tokens via the keyless public API. |
| FR-1.5.6 | Postings older than the configured freshness cutoff are marked inactive and excluded from browse/search; re-ingestion reactivates them. |
| FR-1.5.7 | Each ingestion run logs, per source, counts of fetched / kept / filtered-out / inserted / updated. |
| NFR-1.5.1 | No live network calls in tests; all adapters and the ingestion flow are tested against mocked HTTP / stubbed adapters. |
| NFR-1.5.2 | One failing source or malformed posting never aborts the others (existing per-source and per-board try/catch preserved; filter/extractor are null-safe). |
| NFR-1.5.3 | All new config has safe defaults; the system runs with zero new env vars set. |
| NFR-1.5.4 | Filtering + skill extraction adds negligible latency (pure in-memory string matching; no I/O). |

---

## Data Model Changes

No new tables. One additive enum value and one optional index.

### Enum Change

```sql
-- Add the Ashby provenance to the job_source enum. PostgreSQL 16 allows ALTER TYPE ... ADD VALUE
-- inside a transaction as long as the new value is not used in the same transaction (it is not here).
ALTER TYPE job_source ADD VALUE IF NOT EXISTS 'ASHBY';
```

### Index (supporting the freshness sweep + default sort)

```sql
-- Speeds the deactivate-stale sweep and the default browse ordering.
CREATE INDEX IF NOT EXISTS idx_job_postings_active_posted_at
    ON job_postings (is_active, posted_at DESC);
```

> No `DROP`, no `NOT NULL` without default, no destructive change — additive and safe per the
> Deploy Rule. The `is_active` and `posted_at` columns already exist (V3).

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V16 | `V16__add_ashby_job_source.sql` | `ALTER TYPE job_source ADD VALUE IF NOT EXISTS 'ASHBY';` |
| V17 | `V17__job_postings_active_posted_idx.sql` | Create `idx_job_postings_active_posted_at`. |

> Two files because `ALTER TYPE ... ADD VALUE` must be committed before the new value can be
> referenced; keeping the index in a separate migration avoids any same-transaction coupling and
> matches the V5 precedent of isolating enum additions.

---

## API Changes

No new endpoints and no changes to existing endpoint contracts. Behavior changes only:

- `GET /api/v1/jobs` and `GET /api/v1/jobs/search` return a smaller, on-target, fresher result set
  because irrelevant and stale postings are no longer stored/active. Response JSON shape is
  unchanged. The `skills` array will be populated for Greenhouse/Lever/Ashby postings (previously
  empty).
- `POST /api/v1/jobs/ingest/{source}` (existing manual-trigger endpoint, if present) now returns
  an `IngestionResult` that additionally reports `filteredOut`. See below.

### Modified response: `IngestionResult`

`JobIngestionService.IngestionResult` gains a `filteredOut` count (postings dropped by the relevance
filter). Any controller/endpoint that returns it reflects the new field:

**Response (200):**
```json
{
  "source": "GREENHOUSE",
  "fetched": 240,
  "kept": 31,
  "filteredOut": 209,
  "inserted": 12,
  "updated": 19
}
```

> `kept == inserted + updated`. `fetched == kept + filteredOut`.

**Error cases:** unchanged from Phase 1 (`400` unknown/disabled source, `401` if the trigger
endpoint requires JWT — preserve existing auth posture).

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `JobTargetProperties` | `com.careercopilot.jobs` | `@ConfigurationProperties("jobs.targets")` — binds role/title keywords, location terms, experience terms, skill hints. |
| `JobRelevanceFilter` | `com.careercopilot.jobs.source` | Pure predicate: `boolean isRelevant(NormalizedJob)`. Null-safe, case-insensitive keyword/location matching. |
| `SkillExtractor` | `com.careercopilot.jobs.source` | `List<String> extract(String title, String fullText)` using the injected `SkillAliasMap`. Returns a de-duplicated, canonical skill list. |
| `AshbyJobSource` | `com.careercopilot.jobs.source` | New `JobSourceAdapter` for Ashby boards (keyless JSON). |
| `JobIngestionService` (modified) | `com.careercopilot.jobs` | Apply filter + skill extraction between `adapter.fetch()` and upsert; add `filteredOut` to result; new `deactivateStale()` scheduled sweep. |
| `SkillAliasMap` (reused, unmodified) | `com.careercopilot.scoring` | Existing alias-normalized skill matcher; injected read-only into `SkillExtractor`. |

### Interaction Flow — ingestion

```
@Scheduled ingestAll()
  → for each enabled JobSourceAdapter:
      → adapter.fetch()                         // List<NormalizedJob> (per-board try/catch inside adapter)
      → for each NormalizedJob n:
          → if !JobRelevanceFilter.isRelevant(n): filteredOut++, continue
          → if n.skills is empty:
                n = n.withSkills(SkillExtractor.extract(n.title(), n.fullText()))
          → upsert by (source, externalId): insert or JobPostingMapper.merge (existing)
      → log fetched/kept/filteredOut/inserted/updated
```

### Interaction Flow — freshness sweep

```
@Scheduled deactivateStale()
  → cutoff = now - jobs.freshness.max-age-days
  → repository.deactivateOlderThan(cutoff)      // UPDATE job_postings SET is_active=false
                                                //   WHERE is_active=true AND COALESCE(posted_at, ingested_at) < cutoff
  → log deactivatedCount
  // Re-activation happens naturally: JobPostingMapper.merge() sets is_active=true on re-ingest.
```

### `JobRelevanceFilter` matching rules (deterministic)

- **Title/role match (required):** lowercased `title` (fallback `fullText` first ~500 chars)
  contains at least one configured `titleKeyword`. Keywords are matched as whole-word / substring
  (case-insensitive). Empty keyword list ⇒ everything passes the title check (back-compat escape
  hatch).
- **Location match (required only if `locationTerms` non-empty):** posting is `remote == true`
  **or** lowercased `location` contains at least one configured `locationTerm`. Null location with
  non-empty location terms and `remote == false` ⇒ filtered out.
- **Experience terms:** advisory only in v1 — used to *boost* nothing yet; reserved for future
  ranking. (Documented so config authors know the field exists; not a hard filter, to avoid
  dropping postings that omit seniority in the title.)

> Rationale for keeping experience as non-hard-filter: many new-grad/entry roles are titled plainly
> ("Software Engineer") without a seniority token, so a hard experience filter would over-drop. A
> future ranking phase can score on it.

### `NormalizedJob` change

Add a copy helper so the extractor can enrich without a full reconstruct:

```java
public NormalizedJob withSkills(List<String> skills) { /* record copy with new skills */ }
```

(Compact-canonical: implemented as a straightforward `new NormalizedJob(...)` copy.)

---

## LLM Tasks

**N/A for this phase.** All filtering and skill extraction is deterministic. This explicitly avoids
LLM cost per the Cost Discipline rule.

---

## Rendering Tasks

**N/A for this phase.** No resume rendering.

---

## Configuration

New/changed keys in `application.yml` (all env-overridable; defaults shown):

```yaml
jobs:
  ingestion:
    cron: ${JOBS_INGESTION_CRON:0 0 */6 * * *}
  freshness:
    max-age-days: ${JOBS_FRESHNESS_MAX_AGE_DAYS:45}
    sweep-cron: ${JOBS_FRESHNESS_SWEEP_CRON:0 30 * * * *}
  adapters:
    enabled: ${JOBS_ADAPTERS_ENABLED:REMOTEOK,GREENHOUSE,LEVER,ASHBY}
  targets:
    title-keywords: ${JOBS_TARGET_TITLE_KEYWORDS:>-
      machine learning,ml engineer,ai engineer,applied scientist,deep learning,
      llm,genai,software engineer,software development engineer,backend engineer,
      platform engineer,systems engineer,quantitative developer,quantitative researcher,
      quant,hft,trading systems}
    location-terms: ${JOBS_TARGET_LOCATION_TERMS:india,bengaluru,bangalore,hyderabad,
      pune,delhi,gurgaon,gurugram,noida,mumbai,chennai,remote,singapore,japan,tokyo}
    experience-terms: ${JOBS_TARGET_EXPERIENCE_TERMS:intern,new grad,new-grad,graduate,
      entry level,entry-level,junior,associate}
  greenhouse:
    base-url: ${GREENHOUSE_BASE_URL:https://boards-api.greenhouse.io}
    companies: ${GREENHOUSE_COMPANIES:databricks,gitlab,stripe,coinbase,airbnb,
      razorpay,postman,freshworks,zerodha,credavenue}
  lever:
    base-url: ${LEVER_BASE_URL:https://api.lever.co}
    companies: ${LEVER_COMPANIES:voleon,hudsonrivertrading,spotify}
  ashby:
    base-url: ${ASHBY_BASE_URL:https://api.ashbyhq.com}
    companies: ${ASHBY_COMPANIES:ramp,notion,linear,openai,mistral}
```

> The specific default company tokens above are **candidates** — each must be verified during
> implementation to return a live public board (tokens vary per employer and some quant firms do not
> expose keyless boards). Implementation task: validate each token returns HTTP 200 with a jobs
> array; drop any that don't and note substitutions in `progress_report.md`. Location terms make
> the pooled boards effectively India/Remote/APAC-targeted even though the boards themselves are
> global.

---

## Test Plan

### Unit Tests

| Test class | What it tests | Type |
|---|---|---|
| `JobRelevanceFilterTest` | Keeps a matching ML/SDE/quant title; drops an off-target (designer/sales) title; drops a non-remote non-India location when location terms set; keeps remote regardless of location; empty keyword list passes through; null title/location safe. | Unit |
| `SkillExtractorTest` | Extracts canonical skills from a description containing "Python, PyTorch, AWS"; de-duplicates aliases (e.g. "JS"→"JavaScript"); returns empty list for sk-less text; null-safe on null fullText. | Unit |
| `AshbyJobSourceTest` | Maps a stubbed Ashby JSON payload into `NormalizedJob` (title, company, location, remote, url, externalId, postedAt); array-vs-error payload handling; per-board failure isolation. | Unit (mocked `RestClient`) |
| `JobIngestionServiceTest` (extended) | Filter drops off-target postings before upsert; `filteredOut` counted; skill extraction runs only when source skills empty; `deactivateStale()` calls repository with correct cutoff. | Unit (stubbed adapters + mocked repo) |

### Integration Tests

| Test class | What it tests | Type |
|---|---|---|
| `JobIngestionV2IntegrationTest` | Full ingest with a stubbed adapter returning mixed relevant/irrelevant postings against Testcontainers Postgres+pgvector: only relevant rows persisted, skills populated, `filteredOut` correct, re-ingest updates in place. | Integration |
| `JobFreshnessIntegrationTest` | Seed active postings with old/new `posted_at`; run `deactivateStale()`; assert old rows `is_active=false` and absent from `GET /api/v1/jobs`; re-ingest of an old external_id reactivates it. | Integration |
| `AshbyIngestionIntegrationTest` | Ingest via a wired `AshbyJobSource` backed by a mock web server (no live network) → `ASHBY` rows persisted with the new enum value. | Integration |

> Existing tests preserved and updated for the new `IngestionResult.filteredOut` field:
> `JobIngestionServiceTest`, `JobIngestionIntegrationTest`, `GreenhouseJobSourceTest`,
> `LeverJobSourceTest`, `RemoteOkJobSourceTest`, `JobBrowseIntegrationTest`.

### Guardrail Tests

N/A (no LLM tasks this phase).

---

## Deploy Steps

### Pre-deploy Checklist

- [ ] `./mvnw test` — all tests green (run locally; Testcontainers needs Docker)
- [ ] `./mvnw spotless:check` — formatting clean
- [ ] Flyway V16/V17 reviewed — additive only (enum add + index)
- [ ] Default board tokens verified to return live public boards; dead tokens removed
- [ ] No secrets committed (all new adapters are keyless)

### Environment Variables Required

| Variable | Description | Example / Default |
|---|---|---|
| `JOBS_ADAPTERS_ENABLED` | Comma-separated enabled sources | `REMOTEOK,GREENHOUSE,LEVER,ASHBY` |
| `JOBS_TARGET_TITLE_KEYWORDS` | Comma-separated target role/title keywords | *(see config defaults)* |
| `JOBS_TARGET_LOCATION_TERMS` | Comma-separated location terms (or empty to disable location filter) | `india,remote,singapore` |
| `JOBS_TARGET_EXPERIENCE_TERMS` | Reserved for future ranking | *(see defaults)* |
| `JOBS_FRESHNESS_MAX_AGE_DAYS` | Age after which a posting is deactivated | `45` |
| `JOBS_FRESHNESS_SWEEP_CRON` | Cron for the stale sweep | `0 30 * * * *` |
| `GREENHOUSE_COMPANIES` | Greenhouse board tokens | *(see defaults)* |
| `LEVER_COMPANIES` | Lever board tokens | *(see defaults)* |
| `ASHBY_COMPANIES` | Ashby board tokens | *(see defaults)* |
| `ASHBY_BASE_URL` | Ashby posting API base URL | `https://api.ashbyhq.com` |

> None are required — all have safe in-code defaults (NFR-1.5.3).

### DigitalOcean Deploy Steps

1. Push to `main` — GitHub Actions CI runs `./mvnw test`.
2. On green CI: DigitalOcean App Platform auto-deploys.
3. Flyway V16/V17 run automatically on startup (verify no errors in logs).
4. Smoke test: `GET /actuator/health` → `{"status":"UP"}`.
5. Trigger/await one ingestion cycle; `GET /api/v1/jobs?sort=postedAt,desc` → confirm the list is
   on-target (AI/ML/SDE/quant), skills populated, and dated recently.
6. Confirm ingestion logs show non-zero `filteredOut` (proves the relevance filter is active).

---

## Definition of Done

- [ ] Spec status set to APPROVED before implementation begins
- [ ] `JobRelevanceFilter`, `SkillExtractor`, `AshbyJobSource`, `JobTargetProperties` implemented
- [ ] `JobIngestionService` applies filter + skill extraction and reports `filteredOut`
- [ ] `deactivateStale()` sweep implemented and scheduled; browse sorts `postedAt,desc` by default
- [ ] `SkillAliasMap` reused read-only (no change to the scoring slice)
- [ ] Flyway V16/V17 applied cleanly (no startup errors)
- [ ] All new + updated unit tests pass
- [ ] All integration tests pass (Testcontainers, no live network)
- [ ] `/review` checklist passes (architecture, privacy, testing rules)
- [ ] Default board tokens verified live; substitutions noted in `progress_report.md`
- [ ] CLAUDE.md Phase 1 note updated to reflect ingestion v2
- [ ] `progress_report.md` updated with this phase's work, decisions, issues
- [ ] Deployed to DigitalOcean and smoke-tested (on-target, fresh list confirmed)
- [ ] Spec status set to IMPLEMENTED
