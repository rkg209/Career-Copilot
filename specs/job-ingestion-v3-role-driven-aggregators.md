# Spec: Job Ingestion v3 — Role-Driven Aggregator Search

## Header

| Field | Value |
|---|---|
| Phase | Phase 1.6 — Job Ingestion v3 (Role-Driven Aggregator Search) |
| Status | APPROVED |
| Version | 1.0 |
| Author | Rahul |
| Last Updated | 2026-07-06 |

---

## Goal

The product's core flow is: **a user picks a role (e.g. "AI/ML Engineer") and sees fresh, relevant
India + Remote job listings for it.** The v1/v2 pipeline can't serve this — it polls a few whole
boards and filters globally, giving thin, generic coverage. This phase pivots ingestion to a
**role-driven, query-based** model that matches how free job aggregators actually work: for each
curated **role preset**, we query aggregator search APIs (Adzuna, Jooble) for India + Remote,
tag the results with that role, and cache them in `job_postings`. Whole-board ATS sources
(Greenhouse/Lever/Ashby) continue to run and are tagged by role via keyword match, adding
first-party quality. Users read the **cached** corpus filtered by role — no live API call per
request — so the free API quotas (Adzuna ~1000 calls/month, Jooble free key) are spent *per role*,
not *per user*, and the product scales to many users while staying free. The outcome: selecting
"AI/ML Engineer" returns a broad, current set of matching Indian roles instead of a static generic
list.

---

## Scope

### In Scope

1. **Role presets (`jobs.roles` config)** — a small curated launch set, each mapping a role key to
   search keywords + title-match aliases:
   - `ai-ml-engineer` — machine learning, ML engineer, AI engineer, applied scientist, research
     scientist, deep learning, LLM, GenAI
   - `software-engineer` — software engineer, SDE, backend engineer, full stack, platform engineer
   - `data-scientist` — data scientist, data analyst, analytics engineer
   - `quant` — quantitative developer, quantitative researcher, quant, trading systems
   Adding a role later = one config entry. (The "research/applied scientist" interest folds into
   `ai-ml-engineer` for launch; can split later.)
2. **`QueryableJobSource` abstraction** — a new interface `List<NormalizedJob> fetch(RoleQuery)`
   for search-based sources, parallel to the existing whole-board `JobSourceAdapter`. `RoleQuery`
   carries the role key, the search keywords, and a location term.
3. **Adzuna refactor to query-driven** — `AdzunaJobSource` implements `QueryableJobSource`: it
   issues one search per (role, location) using Adzuna's `what` + `where` params against
   `country=in`, paginating up to a configured page cap. (Its old no-arg whole-board `fetch()` is
   removed from the scheduled path; Adzuna is no longer a plain `JobSourceAdapter`.)
4. **New `JoobleJobSource` (query-driven aggregator)** — keyless-except-free-key adapter hitting
   Jooble's `POST https://jooble.org/api/{key}` search endpoint with `{keywords, location}`;
   maps results into `NormalizedJob`. Second aggregator for breadth.
5. **Role tagging on `job_postings`** — new `matched_roles text[]` column (Flyway V18). Aggregator
   results are tagged with the querying role; whole-board (ATS) results are tagged with every role
   whose keywords match the posting (reusing the v2 keyword-match logic). A single posting may
   carry multiple roles.
6. **Quota-safe cached ingestion** — a per-role scheduled refresh queries each enabled
   `QueryableJobSource` for each (role × location) and upserts role-tagged rows; whole-board
   sources run as today and are role-tagged at ingestion. Users never trigger a live aggregator
   call.
7. **API** — extend `GET /api/v1/jobs` and `GET /api/v1/jobs/search` with an optional `role`
   filter (returns only postings whose `matched_roles` contains it); add
   `GET /api/v1/jobs/roles` returning the available role presets for the UI picker.
8. **Skill extraction** — aggregator results (empty source skills) flow through the existing v2
   `SkillExtractor`, same as ATS sources.

### Out of Scope

- HTML scraping, headless browsers, Playwright, or scraping LinkedIn/Naukri/Indeed/Instahyre
  (ToS-violating and IP-banned on cloud IPs). Aggregators are the legal path to that data.
- Live, per-user aggregator calls or a user-triggered "refresh now" (hybrid) — deferred; the cost
  model must stay per-role.
- User-defined free-text role search (only curated presets at launch). The `role` param accepts a
  known preset key; arbitrary text is out.
- Paid tiers, paid proxies, SerpAPI/Google Jobs, Careerjet (Careerjet may be added later as a third
  aggregator — not this phase).
- Location beyond India + Remote (config-driven; more locations are a later config change).
- Any change to `auth`, `profile`, `tailoring`, `scoring`, `intel`. Reuses `NormalizedJob`,
  `JobPostingMapper`, `(source, external_id)` dedup, `SkillExtractor`, `JobRelevanceFilter`,
  `JobTargetProperties` read-only/extended-in-place within the `jobs` slice.
- Frontend implementation is described at the API contract level; the React role-picker page is a
  follow-up UI task tracked in the DoD but not detailed here.

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-1.6.1 | The system exposes a curated set of role presets, each with search keywords, via config and `GET /api/v1/jobs/roles`. |
| FR-1.6.2 | For each role preset × configured location, ingestion queries each enabled query-driven aggregator and upserts the results tagged with that role. |
| FR-1.6.3 | Whole-board (ATS) postings are tagged with every role whose keywords match, at ingestion. |
| FR-1.6.4 | `GET /api/v1/jobs` and `/search` accept an optional `role` param and return only postings tagged with that role. |
| FR-1.6.5 | Adzuna is queried for India (`country=in`) using role keywords; Jooble is queried via its free POST API using role keywords + location. |
| FR-1.6.6 | Users reading job listings never trigger a live aggregator API call; they read the cached DB corpus. |
| FR-1.6.7 | `JobPostingResponse` exposes `ingestedAt` (when we stored/last-refreshed the row) and `matchedRoles`, so recency of each JD in our DB is visible in the API/UI. |
| FR-1.6.8 | A one-time operational reset clears all existing `job_postings` before the first v3 ingestion so the corpus reflects only the new role-driven pipeline. |
| NFR-1.6.1 | Aggregator API usage is bounded per run: `rolesCount × locationsCount × sourcesCount × pageCap` calls, independent of user traffic; defaults stay within Adzuna's ~1000/month free tier. |
| NFR-1.6.2 | No live network in tests; aggregator adapters and ingestion are tested with mocked HTTP / stubbed sources. |
| NFR-1.6.3 | One failing role, source, or page never aborts the rest (per-unit try/catch). |
| NFR-1.6.4 | Secrets (`ADZUNA_APP_ID`, `ADZUNA_APP_KEY`, `JOOBLE_API_KEY`) come only from env; absent keys disable that source cleanly (logged, skipped), never crash startup. |

---

## Data Model Changes

One additive column. No new tables.

### Column Change

```sql
-- Roles this posting matched (aggregator query role, and/or ATS keyword-matched roles).
-- Empty array = matched no configured role (e.g. legacy rows). Filtered via array containment.
ALTER TABLE job_postings
    ADD COLUMN IF NOT EXISTS matched_roles TEXT[] NOT NULL DEFAULT '{}';

-- GIN index for `matched_roles @> ARRAY[:role]` containment lookups in browse/search.
CREATE INDEX IF NOT EXISTS idx_job_postings_matched_roles
    ON job_postings USING GIN (matched_roles);
```

> Additive and safe (NOT NULL **with** DEFAULT on an existing table is allowed per the Deploy Rule).
> No drops. Existing rows backfill to `'{}'`.

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V18 | `V18__job_postings_matched_roles.sql` | Add `matched_roles text[]` + GIN index. |

> Depends on V16/V17 from Job Ingestion v2 already being applied.

---

## API Changes

### Modified: `GET /api/v1/jobs`

Adds optional `role` query param.

**Request:** `GET /api/v1/jobs?role=ai-ml-engineer&page=0&size=20&sort=postedAt,desc`

- `role` (optional): a known preset key. When present, only postings whose `matched_roles`
  contains it are returned. When absent, behavior is unchanged (all active postings).

**Response (200):** unchanged `PageResponse<JobPostingResponse>` shape.

**Error cases:**
- `400` — `role` is a non-empty value that is not a known preset key.
- (existing) `400` — unsupported sort field.

### Modified: `GET /api/v1/jobs/search`

Adds the same optional `role` param, combined (AND) with the existing filters (`q`, `location`,
`remoteType`, `employmentType`, `salaryMin`, `postedAfter`).

**Request:** `GET /api/v1/jobs/search?role=quant&location=india&remoteType=remote&page=0&size=20`

**Response (200):** unchanged `PageResponse<JobPostingResponse>`.

**Error cases:** `400` for unknown `role`, unknown `remoteType`/`employmentType`, bad `postedAfter`,
unsupported sort (existing behaviors preserved).

### New: `GET /api/v1/jobs/roles`

Public. Returns the role presets for the UI picker.

**Response (200):**
```json
{
  "roles": [
    { "key": "ai-ml-engineer", "label": "AI / ML Engineer" },
    { "key": "software-engineer", "label": "Software Engineer" },
    { "key": "data-scientist", "label": "Data Scientist / Analyst" },
    { "key": "quant", "label": "Quant / Trading" }
  ]
}
```

**Error cases:** none (always 200; empty list if none configured).

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `RoleProperties` | `com.careercopilot.jobs` | `@ConfigurationProperties("jobs")` binding `roles` (key → {label, keywords}) and `search-locations`. |
| `RoleQuery` | `com.careercopilot.jobs.source` | Record: `roleKey`, `List<String> keywords`, `String location`. Input to a `QueryableJobSource`. |
| `QueryableJobSource` | `com.careercopilot.jobs.source` | Interface: `JobSource id()`, `List<NormalizedJob> fetch(RoleQuery query)`. |
| `AdzunaJobSource` (refactored) | `com.careercopilot.jobs.source` | Implements `QueryableJobSource`; one Adzuna `what`/`where` search per query, `country=in`, paginated to a cap. Skipped when keys blank. |
| `JoobleJobSource` (new) | `com.careercopilot.jobs.source` | Implements `QueryableJobSource`; `POST /api/{key}` with `{keywords, location}`. Skipped when key blank. |
| `RoleTagger` | `com.careercopilot.jobs.source` | Given a `NormalizedJob`, returns the set of role keys whose keywords match its title/text (reuses the v2 keyword-match approach). Used to tag whole-board results. |
| `JobIngestionService` (extended) | `com.careercopilot.jobs` | Adds a per-role query pass over `QueryableJobSource`s; tags aggregator rows with the query role and whole-board rows via `RoleTagger`; merges `matched_roles` on upsert. |
| `JobService` / `JobPostingRepository` (extended) | `com.careercopilot.jobs` | `role` filter on list/search via `matched_roles @> ARRAY[:role]`; `roles()` returns presets. |
| `JobController` (extended) | `com.careercopilot.jobs` | New `role` param on list/search; new `GET /roles`. Validates `role` against known presets. |
| `RolePresetResponse` / `RolesResponse` | `com.careercopilot.jobs.dto` | DTOs for `GET /roles`. |

### Interaction Flow — role-driven ingestion (scheduled)

```
@Scheduled ingestAll()
  # (A) query-driven aggregators, per role × location
  → for each role in jobs.roles:
      → for each location in jobs.search-locations:   // e.g. "India", "Remote"
          → RoleQuery q = (roleKey, role.keywords, location)
          → for each enabled QueryableJobSource src (Adzuna, Jooble):
              → try: results = src.fetch(q)            // paginated, per-source try/catch
              → for each NormalizedJob n in results:
                    n = skillExtractor.enrichIfEmpty(n)
                    upsert by (source, externalId), merging roleKey into matched_roles
  # (B) whole-board ATS sources, tagged by keyword match  (unchanged fetch)
  → for each enabled JobSourceAdapter src (Greenhouse, Lever, Ashby, RemoteOK):
      → for each NormalizedJob n in src.fetch():
          → if !relevanceFilter.isRelevant(n): filteredOut++, continue
          → roles = RoleTagger.tag(n)                  // 0..N role keys
          → n = skillExtractor.enrichIfEmpty(n)
          → upsert by (source, externalId), merging roles into matched_roles
```

### Interaction Flow — read path (user picks a role)

```
Client → GET /api/v1/jobs?role=ai-ml-engineer
  → JobController.list(role, page, size, sort)
      → validate role is a known preset (else 400)
      → JobService.list(role, ...)
          → repository.findByRole(role, pageable)   // WHERE is_active AND matched_roles @> ARRAY[:role]
  → PageResponse<JobPostingResponse>     // read straight from cached DB; no aggregator call
```

### `matched_roles` merge semantics

On upsert, `matched_roles` is the **union** of the existing row's roles and the newly derived roles
(so a posting surfaced by the Adzuna "quant" query and also matching the ATS "software-engineer"
keyword ends up tagged with both). `JobPostingMapper.merge` is extended to union rather than
overwrite `matched_roles` (all other fields keep their overwrite semantics).

### Quota arithmetic (defaults)

`4 roles × 2 locations × 2 aggregators × pageCap(1 for Jooble, ≤2 for Adzuna)` ≈ **≤ 24 calls per
run**. At a 12h cadence → ~48/day → ~1,440/month across both providers. Adzuna alone stays near
~700/month (4×2×2×~1 usable page for narrow queries) — inside the ~1000 free tier. `pageCap` and
cadence are config-tunable if a provider tightens limits. (Documented so operators can throttle.)

---

## LLM Tasks

**N/A for this phase.** Ingestion, tagging, and skill extraction are deterministic. No LLM cost.

---

## Rendering Tasks

**N/A for this phase.**

---

## Configuration

```yaml
jobs:
  ingestion:
    cron: ${JOBS_INGESTION_CRON:0 0 */12 * * *}   # 12h cadence (was 6h) to conserve quota
  search-locations: ${JOBS_SEARCH_LOCATIONS:India,Remote}
  roles:
    ai-ml-engineer:
      label: "AI / ML Engineer"
      keywords: ${JOBS_ROLE_AI_ML_KEYWORDS:machine learning,ml engineer,ai engineer,applied scientist,research scientist,deep learning,llm,genai}
    software-engineer:
      label: "Software Engineer"
      keywords: ${JOBS_ROLE_SWE_KEYWORDS:software engineer,software development engineer,backend engineer,full stack,platform engineer}
    data-scientist:
      label: "Data Scientist / Analyst"
      keywords: ${JOBS_ROLE_DS_KEYWORDS:data scientist,data analyst,analytics engineer}
    quant:
      label: "Quant / Trading"
      keywords: ${JOBS_ROLE_QUANT_KEYWORDS:quantitative developer,quantitative researcher,quant,trading systems,hft}
  adapters:
    enabled: ${JOBS_ADAPTERS_ENABLED:REMOTEOK,GREENHOUSE,LEVER,ASHBY,ADZUNA,JOOBLE}
  adzuna:
    base-url: ${ADZUNA_BASE_URL:https://api.adzuna.com}
    app-id: ${ADZUNA_APP_ID:}
    app-key: ${ADZUNA_APP_KEY:}
    country: ${ADZUNA_COUNTRY:in}
    results-per-page: ${ADZUNA_RESULTS_PER_PAGE:50}
    page-cap: ${ADZUNA_PAGE_CAP:2}
    currency: ${ADZUNA_CURRENCY:INR}
  jooble:
    base-url: ${JOOBLE_BASE_URL:https://jooble.org}
    api-key: ${JOOBLE_API_KEY:}
    page-cap: ${JOOBLE_PAGE_CAP:1}
```

> `ADZUNA` / `JOOBLE` in `adapters.enabled` are honored only when their keys are set; a blank key
> logs a warning and skips the source (never crashes). The v2 `jobs.targets` block stays for the
> whole-board relevance filter; `RoleTagger` reuses the same keyword-match logic against per-role
> keywords.

---

## Test Plan

### Unit Tests

| Test class | What it tests | Type |
|---|---|---|
| `AdzunaJobSourceTest` (rewritten) | `fetch(RoleQuery)` builds `what`/`where` from keywords+location, maps Adzuna JSON, paginates to cap, throws/skip on blank keys. | Unit (mocked `RestClient`) |
| `JoobleJobSourceTest` | POST body carries keywords+location; maps Jooble `jobs[]` into `NormalizedJob`; blank key → skipped; error payload safe. | Unit (mocked `RestClient`) |
| `RoleTaggerTest` | Tags a posting with all matching role keys; returns empty for off-target; case-insensitive; null-safe. | Unit |
| `RolePropertiesTest` | Binds roles map + keywords + search-locations from properties. | Unit |
| `JobIngestionServiceTest` (extended) | Per-role query pass tags aggregator rows with the query role; whole-board rows tagged via `RoleTagger`; `matched_roles` unioned on re-ingest; one failing role/source doesn't abort others. | Unit (stubbed sources + mocked repo) |
| `JobControllerTest` | `role` param validated against presets (unknown → 400); `GET /roles` returns presets. | Unit (MockMvc) |

### Integration Tests

| Test class | What it tests | Type |
|---|---|---|
| `RoleDrivenIngestionIntegrationTest` | Stubbed `QueryableJobSource` + whole-board adapter against Testcontainers: rows persisted with correct `matched_roles`; a posting matching two roles is tagged with both; re-ingest unions roles. | Integration |
| `JobRoleFilterIntegrationTest` | Seed role-tagged postings; `GET /api/v1/jobs?role=quant` and `/search?role=...` return only matching rows via GIN containment; unknown role → 400. | Integration |
| `JobRolesEndpointIntegrationTest` | `GET /api/v1/jobs/roles` returns the configured presets. | Integration |
| `AdzunaQueryIntegrationTest` | Wired `AdzunaJobSource` backed by a mock server, driven through ingestion for one role → `ADZUNA` rows persisted, tagged, India location parsed. No live network. | Integration |

### Guardrail Tests

N/A (no LLM tasks this phase).

---

## Deploy Steps

### Pre-deploy Checklist

- [ ] `./mvnw test` — all tests green (Testcontainers needs Docker)
- [ ] `./mvnw spotless:check` — formatting clean
- [ ] Flyway V18 reviewed — additive (column with default + GIN index)
- [ ] Adzuna + Jooble free keys obtained and set as env secrets (not committed)
- [ ] Verified one live query per provider returns India results (manual smoke, outside tests)
- [ ] Quota arithmetic re-checked against each provider's current free limit

### Environment Variables Required

| Variable | Description | Example / Default |
|---|---|---|
| `ADZUNA_APP_ID` | Adzuna free app id | *(from adzuna.com/developer)* |
| `ADZUNA_APP_KEY` | Adzuna free app key | *(secret)* |
| `ADZUNA_COUNTRY` | Adzuna country scope | `in` |
| `ADZUNA_PAGE_CAP` | Max pages per (role,location) | `2` |
| `JOOBLE_API_KEY` | Jooble free API key | *(secret)* |
| `JOOBLE_PAGE_CAP` | Max pages per (role,location) | `1` |
| `JOBS_SEARCH_LOCATIONS` | Locations queried per role | `India,Remote` |
| `JOBS_INGESTION_CRON` | Ingestion cadence | `0 0 */12 * * *` |
| `JOBS_ADAPTERS_ENABLED` | Enabled sources | `REMOTEOK,GREENHOUSE,LEVER,ASHBY,ADZUNA,JOOBLE` |
| `JOBS_ROLE_*_KEYWORDS` | Per-role keyword overrides | *(see config defaults)* |

> Aggregator keys are optional to *boot*: absent keys disable that source with a warning. The role
> presets and ATS sources work without any keys.

### DigitalOcean Deploy Steps

1. Push to `main` — GitHub Actions CI runs `./mvnw test`.
2. On green CI: DigitalOcean App Platform auto-deploys; add Adzuna/Jooble secrets to the App spec.
3. Flyway V18 runs automatically (verify no startup errors).
4. Smoke test: `GET /actuator/health` → `{"status":"UP"}`.
5. `GET /api/v1/jobs/roles` → confirm 4 presets.
6. Await/trigger one ingestion cycle; `GET /api/v1/jobs?role=ai-ml-engineer` → confirm role-tagged,
   India/Remote, fresh listings from multiple sources.
7. Confirm ingestion logs show per-role aggregator calls and total call count within quota.

---

## Definition of Done

- [ ] Spec status set to APPROVED before implementation begins
- [ ] `RoleProperties`, `RoleQuery`, `QueryableJobSource`, `JoobleJobSource`, `RoleTagger` implemented
- [ ] `AdzunaJobSource` refactored to `QueryableJobSource` (India, query-driven)
- [ ] `job_postings.matched_roles` added (V18) with GIN index; `JobPostingMapper.merge` unions roles
- [ ] `JobIngestionService` runs the per-role query pass + role-tags whole-board results
- [ ] `GET /api/v1/jobs` + `/search` accept validated `role`; `GET /api/v1/jobs/roles` implemented
- [ ] Users read cached DB corpus only — no live aggregator call on the read path (verified)
- [ ] Aggregator sources skip cleanly when keys are blank (no startup crash)
- [ ] All unit + integration tests pass (Testcontainers, no live network)
- [ ] `/review` checklist passes (architecture, privacy, security-secrets, testing)
- [ ] React role-picker wired to `GET /roles` + `?role=` (follow-up UI task)
- [ ] CLAUDE.md Phase 1 note updated to reflect v3; `progress_report.md` updated
- [ ] Adzuna + Jooble keys provisioned; live smoke confirms India coverage
- [ ] Deployed to DigitalOcean and smoke-tested
- [ ] Spec status set to IMPLEMENTED
