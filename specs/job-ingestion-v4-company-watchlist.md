# Spec: Job Ingestion v4 — Company Watchlist

## Header

| Field | Value |
|---|---|
| Phase | Phase 1.7 — Job Ingestion v4 (Company Watchlist) |
| Status | DRAFT |
| Version | 1.0 |
| Author | Rahul (drafted with Claude) |
| Last Updated | 2026-07-08 |

---

## Goal

The three board-token ATS adapters — `GreenhouseJobSource`, `AshbyJobSource`, `LeverJobSource` —
currently read their company list from a hardcoded `jobs.<source>.companies` entry in
`application.yml` (5–10 companies each, chosen with no India-hiring relevance, e.g. OpenAI, Stripe,
Coinbase). Growing that list today means editing YAML and redeploying. This phase moves the company
list for these three sources into a database table that ingestion reads fresh on every scheduled
run, and adds an authenticated endpoint so any logged-in user can contribute a company — validated
live against the target ATS's public board API before it's accepted, so the watchlist can only ever
contain boards that actually exist and are reachable. The outcome: the board-token sources can grow
organically toward India-relevant employers without a code change or deploy, while Adzuna/Jooble
(already query-driven, no company list needed — see v3 spec) continue to supply broad
keyword-driven coverage independently.

---

## Scope

### In Scope

1. **`job_board_companies` table** — one row per (source, slug) tracked board, with an
   `is_active` flag and an optional `added_by_user_id` (NULL for the seeded defaults).
2. **Seed migration** — the current hardcoded Greenhouse/Ashby/Lever company lists are carried
   forward as seed rows so ingestion coverage does not regress on deploy.
3. **Adapter refactor** — `GreenhouseJobSource`, `AshbyJobSource`, `LeverJobSource` stop taking a
   static `@Value` company list; each reads its active slugs from `JobBoardCompanyRepository` at
   the start of every `fetch()` call, so a newly contributed company is picked up on the very next
   scheduled ingestion run with no restart.
4. **`POST /api/v1/jobs/board-companies`** — authenticated endpoint for a user to submit a
   candidate `(source, slug)`. The slug is validated live against that ATS's public board endpoint
   (a cheap read, not a full job fetch) before being accepted; an unreachable/nonexistent board is
   rejected with a clear error instead of silently polluting the watchlist.
5. **Auto-approve moderation model** — a successfully validated contribution is active
   immediately; there is no separate review queue (see "Open Design Questions" below for why, and
   what the escape hatch is if this proves to be a problem).
6. **`GET /api/v1/jobs/board-companies`** — public listing of currently tracked, active boards
   (source + slug + display name), so the UI can show what's already covered before a user
   contributes a duplicate.
7. **Config cleanup** — `jobs.greenhouse.companies` / `jobs.ashby.companies` / `jobs.lever.companies`
   are removed from `application.yml` (superseded by the DB table); `jobs.<source>.base-url`
   properties are unchanged and still used both by the adapters and by the new validator.

### Out of Scope

- Any change to Adzuna/Jooble (`QueryableJobSource`) — they are already company-list-free by
  design (v3 spec). This phase only affects the three board-token `JobSourceAdapter`s.
- A manual review/approval queue for contributions — auto-approve-after-validation is the v1
  decision; see Open Design Questions.
- Letting a user deactivate or delete a company they (or anyone else) contributed — v1 is
  add-only. Deactivation is an admin/SQL operation, same as today.
- A cap on total companies per source, beyond the single configurable soft limit in NFR-1.7.3 —
  no per-user rate limiting or abuse throttling beyond ordinary JWT auth.
- USAJOBS, REMOTEOK, MANUAL_PASTE — these sources are not board-token-based and are untouched.
- Any UI beyond the two endpoints above — a React "suggest a company" form is a follow-up UI task
  tracked in the DoD, not detailed here.

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-1.7.1 | Greenhouse/Ashby/Lever ingestion reads its active company slugs from the database, not static config. |
| FR-1.7.2 | A deploy carries forward today's hardcoded company lists as seed data — ingestion coverage does not regress. |
| FR-1.7.3 | An authenticated user can submit `POST /api/v1/jobs/board-companies` with `{source, slug}`. |
| FR-1.7.4 | A submitted slug is validated with a live read against the target ATS's public board API before being persisted; an unreachable/nonexistent board returns `422`, not a silent no-op. |
| FR-1.7.5 | A validated, non-duplicate submission is active immediately (no manual approval step) and is picked up by the next scheduled ingestion run. |
| FR-1.7.6 | Resubmitting a `(source, slug)` that is already active returns `409`; resubmitting one that exists but is inactive reactivates it. |
| FR-1.7.7 | `GET /api/v1/jobs/board-companies` (public) lists active tracked boards so a UI can show existing coverage. |
| NFR-1.7.1 | No live network calls in tests — the ATS validation client is mocked/stubbed in all unit and integration tests. |
| NFR-1.7.2 | One bad company slug never aborts ingestion for the rest — the existing per-board try/catch in each adapter is preserved. |
| NFR-1.7.3 | A configurable soft cap (`jobs.board-companies.max-per-source`, default 100) rejects further contributions to a source once reached, returning `429`, to bound per-run ingestion HTTP call volume. |
| NFR-1.7.4 | Slug input is validated against a strict format (`^[a-z0-9][a-z0-9-]{0,63}$`, case-insensitive, normalized to lowercase) before any outbound validation call, so it is always safe to interpolate into an ATS URL template. |

---

## Data Model Changes

### New Tables

```sql
-- Table: job_board_companies
-- Tracks which company boards each board-token ATS adapter (Greenhouse/Ashby/Lever) polls.
-- Rows with added_by_user_id = NULL are system-seeded defaults carried forward from v1–v3 config.
CREATE TABLE job_board_companies (
    id                BIGSERIAL PRIMARY KEY,
    source            job_source NOT NULL,
    slug              TEXT NOT NULL,
    display_name      TEXT,
    is_active         BOOLEAN NOT NULL DEFAULT true,
    added_by_user_id  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_job_board_companies_source
        CHECK (source IN ('GREENHOUSE', 'LEVER', 'ASHBY')),
    CONSTRAINT uq_job_board_companies_source_slug UNIQUE (source, slug)
);

-- Ingestion reads "active slugs for this source" on every fetch() call.
CREATE INDEX idx_job_board_companies_source_active
    ON job_board_companies (source, is_active);
```

### Seed Data

```sql
-- Carry forward the current hardcoded lists (application.yml, pre-v4) so coverage doesn't regress.
INSERT INTO job_board_companies (source, slug) VALUES
    ('GREENHOUSE', 'databricks'), ('GREENHOUSE', 'gitlab'), ('GREENHOUSE', 'stripe'),
    ('GREENHOUSE', 'coinbase'), ('GREENHOUSE', 'airbnb'), ('GREENHOUSE', 'razorpay'),
    ('GREENHOUSE', 'postman'), ('GREENHOUSE', 'freshworks'), ('GREENHOUSE', 'zerodha'),
    ('GREENHOUSE', 'credavenue'),
    ('ASHBY', 'ramp'), ('ASHBY', 'notion'), ('ASHBY', 'linear'), ('ASHBY', 'openai'),
    ('ASHBY', 'mistral'),
    ('LEVER', 'voleon'), ('LEVER', 'hudsonrivertrading'), ('LEVER', 'spotify')
ON CONFLICT (source, slug) DO NOTHING;
```

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V20 | `V20__job_board_companies.sql` | Creates `job_board_companies` (DDL above) and inserts the seed rows in the same file. |

> Additive only — new table, no changes to existing tables. Safe per the Deploy Rule.

---

## API Changes

### New: `POST /api/v1/jobs/board-companies`

Requires JWT (any authenticated user — no special role).

**Request:**
```json
{
  "source": "GREENHOUSE",
  "slug": "anthropic"
}
```
- `source`: one of `GREENHOUSE`, `LEVER`, `ASHBY` (case-insensitive on input, stored uppercase).
- `slug`: `1–64` chars, matches `^[a-z0-9][a-z0-9-]*$` (case-insensitive; normalized to lowercase
  before validation/storage).

**Response (201):**
```json
{
  "id": 42,
  "source": "GREENHOUSE",
  "slug": "anthropic",
  "displayName": "Anthropic",
  "addedByUserId": "b3f1...uuid",
  "createdAt": "2026-07-08T12:00:00Z"
}
```

**Error cases:**
- `400` — `source` not one of the three supported values, or `slug` fails the format regex.
- `401` — unauthenticated.
- `409` — `(source, slug)` already exists and is active.
- `422` — the live validation read against the ATS's public board endpoint did not find a valid
  board (404, unreachable, or a malformed/error payload). Response body: `{"message": "No public <source> board found for slug 'anthropic'"}`.
- `429` — the source has already reached `jobs.board-companies.max-per-source` active companies.

> A submission for a `(source, slug)` that exists but is currently `is_active = false` reactivates
> the row (200, not 201) rather than erroring or creating a duplicate.

### New: `GET /api/v1/jobs/board-companies`

Public (same pattern as `GET /api/v1/jobs/**`).

**Request:** `GET /api/v1/jobs/board-companies?source=GREENHOUSE` (`source` optional filter)

**Response (200):**
```json
{
  "companies": [
    { "source": "GREENHOUSE", "slug": "databricks", "displayName": "Databricks" },
    { "source": "ASHBY", "slug": "openai", "displayName": "OpenAI" }
  ]
}
```

**Error cases:**
- `400` — `source` present but not one of the three supported values.

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `JobBoardCompany` | `com.careercopilot.jobs` | JPA entity for `job_board_companies`. |
| `JobBoardCompanyRepository` | `com.careercopilot.jobs` | `findBySourceAndSlug`, `findActiveSlugsBySource(JobSource)` (returns `List<String>`, used by the three adapters), `countBySourceAndActiveTrue`. |
| `JobBoardValidator` | `com.careercopilot.jobs.source` | Given `(JobSource, slug)`, performs the cheap live read against that source's public board API and returns whether it's valid plus a best-effort display name. One method per source, keyed off the existing `jobs.<source>.base-url` config already used by the adapters. |
| `JobBoardCompanyService` | `com.careercopilot.jobs` | Orchestrates: format-check → cap-check → duplicate/reactivate-check → `JobBoardValidator` call → persist. Owns the 400/409/422/429 decision logic. |
| `JobBoardCompanyController` | `com.careercopilot.jobs` | `POST`/`GET /api/v1/jobs/board-companies`. Thin — delegates to the service, maps exceptions to status codes. |
| `BoardCompanyRequest` / `BoardCompanyResponse` / `BoardCompaniesResponse` | `com.careercopilot.jobs.dto` | Request/response DTOs, same style as `JobPasteRequest`/`JobPostingResponse`. |
| `GreenhouseJobSource` / `AshbyJobSource` / `LeverJobSource` (modified) | `com.careercopilot.jobs.source` | Constructor no longer takes `@Value("...companies")`; instead takes `JobBoardCompanyRepository` and calls `findActiveSlugsBySource(JobSource.X)` at the top of `fetch()`. Per-board try/catch loop is otherwise unchanged. |

### Interaction Flow — user contributes a company

```
Client
  → JobBoardCompanyController.contribute(BoardCompanyRequest, principal)
  → JobBoardCompanyService.contribute(source, slug, userId)
      → validate slug format (else 400)
      → repository.countBySourceAndActiveTrue(source) >= cap? (else 429)
      → repository.findBySourceAndSlug(source, slug)
          → present + active? → 409
          → present + inactive? → reactivate: set is_active=true, save → 200 BoardCompanyResponse
          → absent? → continue
      → JobBoardValidator.validate(source, slug)
          → invalid/unreachable? → 422 with message
          → valid → displayName (best-effort)
      → repository.save(new JobBoardCompany(source, slug, displayName, active=true, userId))
      → 201 BoardCompanyResponse
```

### Interaction Flow — ingestion picks up new companies (unchanged cadence)

```
@Scheduled ingestAll()
  → for each enabled JobSourceAdapter (Greenhouse, Lever, Ashby, RemoteOK):
      → adapter.fetch()
          [Greenhouse/Ashby/Lever only:]
          → slugs = jobBoardCompanyRepository.findActiveSlugsBySource(this.id())   // fresh DB read
          → for each slug: fetchBoard(slug) in its own try/catch   // unchanged
      → relevanceFilter / roleTagger / upsert / deactivateMissingFrom   // unchanged (see v2/v3 specs)
```

> No change to the ingestion cron, dedup, role-tagging, or disappearance-based deactivation logic —
> those already operate per-source and are agnostic to where the company list came from.

### `JobBoardValidator` per-source checks

| Source | Validation call | Valid response shape | Invalid/not-found signal |
|---|---|---|---|
| GREENHOUSE | `GET {base-url}/v1/boards/{slug}` | 200, JSON object with `name` | 404 |
| ASHBY | `GET {base-url}/posting-api/job-board/{slug}` | 200, JSON object with `jobs` array | 404 |
| LEVER | `GET {base-url}/v0/postings/{slug}?mode=json` | 200, **JSON array** (Lever returns a 200 JSON *object* with an error field for a bad token — same quirk `LeverJobSource.fetchBoard` already guards against) | 200 object (non-array), or non-200 |

`displayName` is taken from Greenhouse's `name` field when present; Ashby/Lever have no reliable
board-level display name in this payload, so `displayName` falls back to the slug itself for those
two sources.

---

## LLM Tasks

**N/A for this phase.** Validation and ingestion remain deterministic HTTP calls; no LLM involved.

---

## Rendering Tasks

**N/A for this phase.**

---

## Configuration

```yaml
jobs:
  board-companies:
    max-per-source: ${JOBS_BOARD_COMPANIES_MAX_PER_SOURCE:100}
  greenhouse:
    base-url: ${GREENHOUSE_BASE_URL:https://boards-api.greenhouse.io}
    # `companies` removed — superseded by job_board_companies table (seeded in V20).
  ashby:
    base-url: ${ASHBY_BASE_URL:https://api.ashbyhq.com}
    # `companies` removed — superseded by job_board_companies table (seeded in V20).
  lever:
    base-url: ${LEVER_BASE_URL:https://api.lever.co}
    # `companies` removed — superseded by job_board_companies table (seeded in V20).
```

> `JobBoardValidator` reuses the same three `base-url` properties the adapters already read — no
> new base-url config needed.

---

## Open Design Questions

These were deliberately decided rather than left silent, but are flagged here since a human should
confirm the call before implementation:

1. **Auto-approve vs. review queue.** Chosen: auto-approve after live validation. Rationale: the
   validation step already proves the board is real and reachable, which is the main spam/garbage
   vector; a review queue adds a moderation UI and a "pending" state for no clear benefit at this
   scale. Escape hatch if this proves wrong in practice: add an `is_active` toggle path for an
   admin to deactivate a bad-actor contribution after the fact — no schema change needed, the
   column already exists.
2. **No per-user contribution limit.** A single user could contribute up to
   `max-per-source` (100) companies solo. Acceptable for v1 given JWT auth is already required and
   the cap bounds total damage; revisit if abuse is observed.
3. **Display name quality for Ashby/Lever.** Falls back to the raw slug (e.g. `hudsonrivertrading`
   instead of "Hudson River Trading") since neither API's board-level payload reliably exposes a
   human name at this endpoint. Acceptable for v1; a later enhancement could let the contributing
   user optionally supply a display name in the request.

---

## Test Plan

### Unit Tests

| Test class | What it tests | Type |
|---|---|---|
| `JobBoardCompanyServiceTest` | Happy path persists an active row tagged with the contributing user; duplicate active → `ConflictException`/409; inactive duplicate → reactivated (200 path); cap reached → 429; invalid format → 400; validator failure → 422. | Unit (mocked repository + mocked `JobBoardValidator`) |
| `JobBoardValidatorTest` | Greenhouse/Ashby: 200-with-expected-shape → valid; 404 → invalid. Lever: 200-array → valid; 200-object (error payload) → invalid; non-200 → invalid. | Unit (mocked `RestClient`) |
| `JobBoardCompanyControllerTest` | 401 without JWT; 400 on bad `source` enum value or malformed slug; 201 on success (service mocked); `GET` with bad `source` filter → 400. | Unit (MockMvc) |
| `GreenhouseJobSourceTest` / `AshbyJobSourceTest` / `LeverJobSourceTest` (updated) | `fetch()` now sources its company list from a mocked `JobBoardCompanyRepository.findActiveSlugsBySource(...)` instead of a static list; per-board try/catch behavior unchanged. | Unit |

### Integration Tests

| Test class | What it tests | Type |
|---|---|---|
| `JobBoardCompanyContributionIntegrationTest` | Full `POST /api/v1/jobs/board-companies` flow against Testcontainers Postgres with a stubbed/mocked `JobBoardValidator` (no live network): valid submission persists and is visible via `GET`; duplicate active → 409; reactivation path; cap enforcement. | Integration |
| `JobBoardCompanySeedMigrationIntegrationTest` | After V20 runs, `job_board_companies` contains exactly the seeded rows from the current hardcoded lists, all `added_by_user_id IS NULL`. | Integration |
| `JobIngestionCompanyWatchlistIntegrationTest` | A company contributed via the repository (simulating a prior successful contribution) is picked up by the next `GreenhouseJobSource.fetch()`/`ingestFrom()` call without any restart. | Integration |

### Guardrail Tests

N/A (no LLM tasks this phase).

---

## Deploy Steps

### Pre-deploy Checklist

- [ ] `./mvnw test` — all tests green (Testcontainers needs Docker)
- [ ] `./mvnw spotless:check` — formatting clean
- [ ] Flyway V20 reviewed — additive only (new table + seed insert)
- [ ] Confirm seed rows in V20 match the current `application.yml` company lists exactly, before
      those lines are removed from config
- [ ] Manual smoke: submit one real new company via `POST /board-companies` against a local run,
      confirm it's picked up on the next manual ingestion trigger

### Environment Variables Required

| Variable | Description | Example / Default |
|---|---|---|
| `JOBS_BOARD_COMPANIES_MAX_PER_SOURCE` | Soft cap on active companies per board-token source | `100` |

> No new secrets. `GREENHOUSE_BASE_URL` / `ASHBY_BASE_URL` / `LEVER_BASE_URL` already exist and are
> reused by `JobBoardValidator`.

### DigitalOcean Deploy Steps

1. Push to `main` — GitHub Actions CI runs `./mvnw test`.
2. On green CI: DigitalOcean App Platform auto-deploys.
3. Flyway V20 runs automatically — verify no startup errors and that seed rows landed
   (`SELECT count(*) FROM job_board_companies` should match the pre-migration company counts).
4. Smoke test: `GET /actuator/health` → `{"status":"UP"}`.
5. `GET /api/v1/jobs/board-companies` → confirm the seeded companies appear.
6. Submit one real contribution via `POST /api/v1/jobs/board-companies` with a known-good slug →
   confirm 201, then confirm it appears in the next scheduled ingestion run's logs.

---

## Definition of Done

- [ ] Spec status set to APPROVED before implementation begins
- [ ] `job_board_companies` table created (V20) with seed data matching pre-migration config
- [ ] `GreenhouseJobSource`, `AshbyJobSource`, `LeverJobSource` read companies from the repository, not `@Value`
- [ ] `jobs.greenhouse.companies` / `jobs.ashby.companies` / `jobs.lever.companies` removed from `application.yml`
- [ ] `JobBoardValidator` implemented for all three sources, including the Lever array-vs-object quirk
- [ ] `POST /api/v1/jobs/board-companies` implemented with format/cap/duplicate/validation checks and correct status codes
- [ ] `GET /api/v1/jobs/board-companies` implemented (public, optional `source` filter)
- [ ] All unit + integration tests pass (Testcontainers, no live network)
- [ ] `/review` checklist passes (architecture, privacy, security-secrets, testing)
- [ ] React "suggest a company" UI wired to the new endpoints (follow-up UI task)
- [ ] CLAUDE.md Phase 1 note updated to reflect v4; `progress_report.md` updated
- [ ] Deployed to DigitalOcean and smoke-tested
- [ ] Spec status set to IMPLEMENTED
