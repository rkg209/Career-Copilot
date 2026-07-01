# Spec: Phase 1 — Job Ingestion / Portal

## Header

| Field | Value |
|---|---|
| Phase | 1 — Job Ingestion / Portal |
| Status | IMPLEMENTED |
| Version | 1.0 |
| Author | Rahul |
| Last Updated | 2026-07-02 |

> **Implementation note (2026-07-02):** All backend + frontend features implemented; 39/39 tests pass
> (unit + Testcontainers integration against real Postgres/pgvector); spotless + frontend build clean.
> Enums use native Postgres types via `@JdbcTypeCode(NAMED_ENUM)`. Live sources: `REMOTEOK` +
> `MANUAL_PASTE` (Adzuna built, disabled by default). **Remaining before full sign-off: DigitalOcean
> redeploy + prod smoke test** (the two unchecked DoD items below).

> **Amendment 1 (2026-07-02) — keyless ATS adapters:** Post-spec, two additional adapters were added
> behind the same `JobSourceAdapter` interface with zero controller/ingestion changes (validating
> FR-1.6): `GreenhouseJobSource` and `LeverJobSource` (both keyless public ATS board APIs, company
> lists config-driven via `GREENHOUSE_COMPANIES` / `LEVER_COMPANIES`). Migration
> `V5__add_ats_job_sources.sql` additively extends the `job_source` enum with `GREENHOUSE` and
> `LEVER`. Default enabled adapters are now `REMOTEOK,GREENHOUSE` (FR-1.1's "two real external APIs"
> is satisfied by these; Adzuna remains built-but-disabled pending API keys). Test suite is now
> 45/45 (adds `GreenhouseJobSourceTest`, `LeverJobSourceTest`).

> **Schema reconciliation note (applies to all Phase 1+ specs):** Phase 0 deployed `users.id` as
> `UUID` (see `V1__initial_schema.sql`), while `planning/04-database-schema.sql` models it as
> `BIGSERIAL`. Because migrations must be additive, this spec keeps `BIGSERIAL` primary keys for new
> tables (per the planning naming convention) but declares every `*_user_id` foreign key as `UUID` so
> it references the live `users.id`. Confirm this decision before setting the spec to APPROVED.

---

## Goal

Turn the empty authenticated skeleton into a populated job portal — the product's entry point. This
phase introduces a pluggable `JobSource` interface with concrete adapters for real, free job APIs plus
a manual JD-paste path, normalizes every incoming posting into a single internal schema, persists it in
`job_postings`, and exposes a public browsable/searchable listing. After this phase a user who logs in
lands on a portal already full of jobs they can browse, search, and open — with no LLM involvement yet.

---

## Scope

### In Scope
- `JobSource` interface + adapter registry (strategy pattern), one adapter per external source.
- Adapters: `AdzunaJobSource`, `RemoteOkJobSource` (both real free APIs), and `ManualPasteJobSource`.
- JD normalization: map each source's payload into the internal `JobPosting` shape (title, company,
  location, remote flag, employment type, salary range, skills[], responsibilities[], full_text, url).
- Deduplication on `(source, external_id)`; upsert semantics for re-ingested postings.
- Scheduled ingestion job (`@Scheduled`) that polls enabled API adapters on a configurable cron.
- Manual paste endpoint: authenticated user pastes raw JD text → stored as a `MANUAL_PASTE` posting.
- Public browse: paginated `GET /api/v1/jobs`, detail `GET /api/v1/jobs/{id}`.
- Public search: `GET /api/v1/jobs/search` with keyword (FTS), location, remote type, employment type,
  salary, posted-after filters.
- React portal page: job list with filters + pagination, job detail view, manual-paste form.

### Out of Scope
- LLM parsing of JDs (skills/responsibilities extraction here is heuristic/source-provided; deep
  LLM extraction belongs to Phase 4 scoring). No `LlmGateway` in this phase.
- Embedding of job postings (not required until scoring/tailoring retrieval — deferred).
- General HTML scraping adapter (an explicitly-later optional adapter behind the same interface).
- USAJobs adapter (interface-compatible, but deferred; listed as a follow-up adapter).
- Saving/bookmarking jobs to a user (that is the Phase 5 tracker's `SAVED` application state).

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-1.1 | The system shall ingest job postings from at least two real external job APIs. |
| FR-1.2 | The system shall let an authenticated user submit a job posting by pasting raw JD text. |
| FR-1.3 | All postings, regardless of source, shall be normalized to one internal schema. |
| FR-1.4 | Duplicate postings (same source + external id) shall not be inserted twice. |
| FR-1.5 | Any visitor (authenticated or not) shall be able to browse and search postings. |
| FR-1.6 | New `JobSource` adapters shall be addable without changing ingestion or controller code. |
| NFR-1.1 | Ingestion of one source page shall not block API request threads (scheduled/async). |
| NFR-1.2 | A failing external API shall not fail the whole ingestion run (per-adapter isolation). |
| NFR-1.3 | Job list and search responses shall return within 300 ms p95 at 10k postings. |

---

## Data Model Changes

### New Tables

```sql
-- V3__job_postings.sql
-- Enum for source provenance (create type if not already present).
CREATE TYPE job_source AS ENUM ('ADZUNA', 'REMOTEOK', 'USAJOBS', 'MANUAL_PASTE');

CREATE TABLE job_postings (
    id                  BIGSERIAL       PRIMARY KEY,
    source              job_source      NOT NULL,
    external_id         TEXT,                                   -- NULL for manual paste
    title               TEXT            NOT NULL,
    company             TEXT            NOT NULL,
    location            TEXT,
    remote              BOOLEAN         NOT NULL DEFAULT false,
    employment_type     TEXT,
    salary_min          NUMERIC(12, 2),
    salary_max          NUMERIC(12, 2),
    salary_currency     CHAR(3),
    skills              TEXT[]          NOT NULL DEFAULT '{}',
    responsibilities    TEXT[]          NOT NULL DEFAULT '{}',
    full_text           TEXT            NOT NULL,
    url                 TEXT,
    posted_at           TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    pasted_by_user_id   UUID            REFERENCES users (id) ON DELETE SET NULL,
    ingested_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
    is_active           BOOLEAN         NOT NULL DEFAULT true,

    CONSTRAINT uq_job_postings_source_external UNIQUE (source, external_id),
    CONSTRAINT ck_job_postings_salary CHECK (
        salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max
    )
);

CREATE INDEX idx_job_postings_company     ON job_postings (company);
CREATE INDEX idx_job_postings_is_active   ON job_postings (is_active);
CREATE INDEX idx_job_postings_ingested_at ON job_postings (ingested_at DESC);
CREATE INDEX idx_job_postings_posted_at   ON job_postings (posted_at DESC);
CREATE INDEX idx_job_postings_source      ON job_postings (source);
```

```sql
-- V4__job_postings_search_indexes.sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_job_postings_fts ON job_postings
    USING GIN (to_tsvector('english',
        coalesce(title, '') || ' ' || coalesce(company, '') || ' ' || coalesce(full_text, '')));

CREATE INDEX idx_job_postings_title_trgm   ON job_postings USING GIN (title   gin_trgm_ops);
CREATE INDEX idx_job_postings_company_trgm ON job_postings USING GIN (company gin_trgm_ops);
```

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V3 | `V3__job_postings.sql` | `job_source` enum + `job_postings` table + btree indexes |
| V4 | `V4__job_postings_search_indexes.sql` | pg_trgm + FTS/trigram indexes for search |

---

## API Changes

### `GET /api/v1/jobs` — PUBLIC

Paginated listing of active postings. Query params: `page`, `size`, `sort`
(allowed: `postedAt`, `createdAt`, `title`, `company`; default `postedAt,desc`).

**Response (200):**
```json
{
  "content": [
    {
      "id": 42,
      "source": "ADZUNA",
      "title": "Backend Engineer",
      "company": "Acme Corp",
      "location": "Remote — US",
      "remote": true,
      "employmentType": "FULL_TIME",
      "salaryMin": 120000,
      "salaryMax": 160000,
      "salaryCurrency": "USD",
      "skills": ["Java", "Spring", "PostgreSQL"],
      "url": "https://...",
      "postedAt": "2026-06-20T00:00:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 47, "totalPages": 3 }
}
```
**Error cases:** `400 VALIDATION_FAILED` (size > 100 or unsupported sort field).

### `GET /api/v1/jobs/{id}` — PUBLIC

**Response (200):** full posting including `responsibilities[]` and `fullText`.
**Error cases:** `404 RESOURCE_NOT_FOUND` (no such id or `is_active = false`).

### `GET /api/v1/jobs/search` — PUBLIC

Query params (all optional, combinable) plus pagination: `q`, `location`, `remoteType`
(`remote|hybrid|onsite`), `employmentType` (`full_time|contract|part_time`), `salaryMin`,
`postedAfter` (ISO date). Returns the same paginated envelope as `GET /api/v1/jobs`;
`page.totalElements` reflects the filtered count.
**Error cases:** `400 VALIDATION_FAILED` (bad enum value, malformed date, size > 100).

### `POST /api/v1/jobs/paste` — AUTHENTICATED

**Request:**
```json
{
  "title": "string (required, 1..255)",
  "company": "string (required, 1..255)",
  "location": "string | null",
  "url": "string | null",
  "rawText": "string (required, 50..20000) — pasted JD body"
}
```
**Response (201):** the created `JobPostingResponse` (source `MANUAL_PASTE`, `external_id = null`,
`pasted_by_user_id` = caller). `full_text` = `rawText`; `skills`/`responsibilities` default empty
(populated later by Phase 4 extraction).
**Error cases:** `400 VALIDATION_FAILED`; `401 AUTHENTICATION_REQUIRED`.

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `JobController` | `com.careercopilot.jobs` | List, detail, search, paste endpoints |
| `JobService` | `com.careercopilot.jobs` | Query orchestration, paste creation, DTO mapping |
| `JobIngestionService` | `com.careercopilot.jobs` | Runs each enabled adapter; upsert + dedup |
| `JobPostingRepository` | `com.careercopilot.jobs` | Spring Data JPA + custom search query |
| `JobPosting` | `com.careercopilot.jobs` | JPA entity mapping `job_postings` |
| `JobSource` (interface) | `com.careercopilot.jobs.source` | `List<NormalizedJob> fetch()` + `job_source id()` |
| `AdzunaJobSource` | `com.careercopilot.jobs.source` | Adzuna REST client + normalizer |
| `RemoteOkJobSource` | `com.careercopilot.jobs.source` | RemoteOK REST client + normalizer |
| `ManualPasteJobSource` | `com.careercopilot.jobs.source` | Wraps a paste DTO into `NormalizedJob` |
| `NormalizedJob` | `com.careercopilot.jobs.source` | Internal normalized record (pre-persist) |
| `JobPostingResponse` / `JobSearchCriteria` / `JobPasteRequest` | `com.careercopilot.jobs.dto` | API DTOs |

### Interaction Flow

```
Scheduled ingestion (cron):
  JobIngestionService.ingestAll()
    → for each JobSource in registry (isolated try/catch per adapter):
        → source.fetch() → List<NormalizedJob>
        → JobPostingRepository.upsertBySourceAndExternalId(normalized)   -- ON CONFLICT DO UPDATE
    → log {source, fetched, inserted, updated, skipped}

Browse:
  GET /api/v1/jobs → JobController.list(pageable)
    → JobService.list(pageable) → JobPostingRepository.findByIsActiveTrue(pageable)
    → returns Page<JobPostingResponse>

Search:
  GET /api/v1/jobs/search → JobController.search(criteria, pageable)
    → JobService.search(criteria, pageable) → JobPostingRepository.search(criteria, pageable)   -- FTS + filters

Paste:
  POST /api/v1/jobs/paste → JobController.paste(req, principal)
    → JobService.createManual(req, userId)
      → ManualPasteJobSource.normalize(req, userId) → JobPostingRepository.save(...)
    → returns JobPostingResponse (201)
```

---

## LLM Tasks

N/A for this phase. Phase 1 does no LLM calls; skills/responsibilities are taken from the source
payload (Adzuna/RemoteOK provide tags) or left empty for manual paste.

---

## Rendering Tasks

N/A for this phase.

---

## Test Plan

### Unit Tests

| Test class | What it tests |
|---|---|
| `AdzunaJobSourceTest` | Maps a canned Adzuna JSON fixture to `NormalizedJob` correctly |
| `RemoteOkJobSourceTest` | Maps a canned RemoteOK JSON fixture; handles missing salary |
| `JobIngestionServiceTest` | One failing adapter does not abort the others; dedup upsert path |
| `JobServiceTest` | Search criteria → repository query args; paste creates `MANUAL_PASTE` |

### Integration Tests

| Test class | What it tests |
|---|---|
| `JobIngestionIntegrationTest` | Upsert against Testcontainers Postgres; re-ingest updates, no dup rows |
| `JobBrowseIntegrationTest` | `GET /jobs` + `/jobs/{id}` pagination + 404 for inactive/missing |
| `JobSearchIntegrationTest` | FTS `q`, location, remoteType, salaryMin filters over seeded data |
| `JobPasteIntegrationTest` | `POST /jobs/paste` 201 when authed, 401 when anonymous, 400 on short text |

External HTTP clients are stubbed (WireMock or MockWebServer); no live external API calls in tests.

---

## Deploy Steps

### Pre-deploy Checklist
- [ ] `./mvnw test` — all tests green
- [ ] `./mvnw spotless:check` — formatting clean
- [ ] V3 + V4 migrations reviewed (additive: new table, new indexes)
- [ ] External API keys stored as DO secrets, not committed
- [ ] Ingestion cron + adapter enable flags set in prod config

### Environment Variables Required

| Variable | Description | Example |
|---|---|---|
| `ADZUNA_APP_ID` | Adzuna API app id | `{secret}` |
| `ADZUNA_APP_KEY` | Adzuna API key | `{secret}` |
| `JOBS_INGESTION_CRON` | Spring cron for ingestion | `0 0 */6 * * *` |
| `JOBS_ADAPTERS_ENABLED` | Comma list of enabled sources | `ADZUNA,REMOTEOK` |

### DigitalOcean Deploy Steps
1. Push to `main` — CI runs compile + test + spotless + frontend build.
2. On green CI: DO App Platform auto-deploys.
3. Flyway applies V3 + V4 on startup.
4. Smoke: `GET /actuator/health` → `UP`; `GET /api/v1/jobs` → 200 (empty page allowed initially).
5. Trigger/wait for first ingestion; confirm `GET /api/v1/jobs` returns postings; paste one JD as a user.

---

## Definition of Done

- [x] Spec status set to APPROVED before implementation begins
- [x] `GET /api/v1/jobs`, `/jobs/{id}`, `/jobs/search`, `POST /jobs/paste` implemented per schemas
- [x] Adzuna + RemoteOK adapters ingest real postings; adding a new adapter needs no controller change
- [x] Dedup verified: re-ingesting the same source page inserts zero duplicate rows
- [x] V3 + V4 Flyway migrations apply cleanly
- [x] All listed unit + integration tests pass with Testcontainers (45/45 after Amendment 1)
- [x] React portal lists, filters, opens, and pastes jobs against the live API
- [x] `/review` checklist passes
- [x] CLAUDE.md updated to Phase 2
- [ ] Deployed to DigitalOcean and smoke-tested
- [ ] Spec status set to IMPLEMENTED
