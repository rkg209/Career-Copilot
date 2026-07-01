# Spec: Phase 6 — Public Company-Intel Aggregation

## Header

| Field | Value |
|---|---|
| Phase | 6 — Public Company-Intel Aggregation |
| Status | DRAFT |
| Version | 1.0 |
| Author | Rahul |
| Last Updated | 2026-07-02 |

> **Schema reconciliation note:** New shared table uses `BIGSERIAL` PK and — by design — has **no**
> `user_id`/`application_id` columns. Confirm before APPROVED.

---

## Goal

Turn private interview logs into a shared, public knowledge base without leaking anyone's identity.
This phase adds the anonymization component, the shared `company_interview_intel` pool, and a public
browse view aggregating de-identified questions and process notes per company. A user chooses to
contribute a specific interview log; `AnonymizationService` scrubs it (strips names, emails, phones,
role-specific identifiers, free-text PII), normalizes the role family, and writes an anonymized record.
Records stay **staged** until at least N=3 anonymized entries exist for a company, then publish. Private
data never leaves the private zone; only scrubbed, pooled, aggregated intel is public.

---

## Scope

### In Scope
- `company_interview_intel` shared table (no PII columns; written only by `AnonymizationService`).
- `AnonymizationService.anonymize()` — the scrubbing pipeline (see `.claude/skills/anonymization.md`):
  strip names/emails/phones/URLs, remove free-text PII, normalize company + role category, drop
  interviewer role if too identifying.
- Minimum-aggregation gate: records `STAGED` until ≥ N (configurable, default 3) per company; then
  batch-publish (`is_published = true`, `published_at`, clear `staging_batch_id`).
- Full implementation of `POST …/interview-logs/{logId}/contribute` (replacing the Phase 5 stub):
  anonymize → insert intel row → mark source log `contributed_at`/`contribution_status`.
- Public browse: `GET /api/v1/intel/companies` (aggregated), `GET /api/v1/intel/companies/{companyName}`.
- React: public company-intel browse page (no auth required), and a "contribute" toggle in the tracker.

### Out of Scope
- Using intel to generate mock questions (Phase 7 consumes this pool).
- Editing/withdrawing a published anonymized record (append-only in v1; withdrawal is v2).
- Cross-company analytics/dashboards beyond per-company aggregation.

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-6.1 | A user can contribute a specific interview log to the public pool. |
| FR-6.2 | Contribution runs anonymization before any shared write (no PII persisted). |
| FR-6.3 | Intel for a company is hidden until ≥ N anonymized entries exist (default 3). |
| FR-6.4 | Any visitor can browse aggregated, de-identified company intel without authenticating. |
| FR-6.5 | A log already contributed cannot be contributed again (409). |
| NFR-6.1 | `company_interview_intel` has no `user_id`/`application_id` and is written only by `AnonymizationService`. |
| NFR-6.2 | Anonymization is covered by tests asserting known PII patterns are removed. |
| NFR-6.3 | The public browse endpoints never expose any private field or user identity. |

---

## Data Model Changes

### New Tables

```sql
-- V13__company_interview_intel.sql
-- SHARED zone: no user_id, no application_id — by design. Written only by AnonymizationService.
CREATE TABLE company_interview_intel (
    id               BIGSERIAL            PRIMARY KEY,
    company          TEXT                 NOT NULL,
    role_category    TEXT,                                   -- normalised role family
    round_type       interview_round_type NOT NULL,
    questions        TEXT[]               NOT NULL DEFAULT '{}',
    process_notes    TEXT,
    difficulty       SMALLINT             CHECK (difficulty BETWEEN 1 AND 5),
    is_published     BOOLEAN              NOT NULL DEFAULT false,
    staging_batch_id UUID,                                   -- groups staged rows per company
    published_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ          NOT NULL DEFAULT now()
);
CREATE INDEX idx_company_intel_company   ON company_interview_intel (company);
CREATE INDEX idx_company_intel_published ON company_interview_intel (company, is_published) WHERE is_published = true;
CREATE INDEX idx_company_intel_staging   ON company_interview_intel (staging_batch_id)      WHERE is_published = false;
CREATE INDEX idx_company_intel_role      ON company_interview_intel (company, role_category) WHERE is_published = true;
CREATE INDEX idx_company_intel_company_trgm ON company_interview_intel USING GIN (company gin_trgm_ops);
```

`interview_round_type` and `contribution_status` enums already exist from Phase 5 (V12).

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V13 | `V13__company_interview_intel.sql` | Shared anonymized intel pool + indexes |

---

## API Changes

### `POST /api/v1/tracker/applications/{id}/interview-logs/{logId}/contribute` — OWNER_ONLY
Full implementation. Anonymizes the log and inserts an intel record; if the company is below threshold
the record is staged (still hidden). **Response (200):**
```json
{ "status": "STAGED", "company": "Acme Corp", "publishedCountForCompany": 2, "threshold": 3 }
```
or `{ "status": "PUBLISHED", ... }` once the threshold is met (batch flips staged rows to published).
This is a **success with deferred outcome** — `STAGED` is 200, not an error.
**Errors:** `403 ACCESS_DENIED`; `404 RESOURCE_NOT_FOUND`; `409 ALREADY_CONTRIBUTED`.

### `GET /api/v1/intel/companies` — PUBLIC
Paginated (`sort=companyName,asc`). Returns per-company aggregates over **published** rows only:
```json
{
  "content": [
    { "company": "Acme Corp", "roundTypes": ["PHONE_SCREEN","TECHNICAL"], "entryCount": 7, "avgDifficulty": 3.4 }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 12, "totalPages": 1 }
}
```
**Errors:** `400 VALIDATION_FAILED` (bad pagination).

### `GET /api/v1/intel/companies/{companyName}` — PUBLIC
Returns published intel for one company, grouped by round type:
```json
{
  "company": "Acme Corp",
  "rounds": [
    {
      "roundType": "TECHNICAL",
      "roleCategory": "Software Engineer",
      "questions": ["Design a rate limiter", "Explain database indexing"],
      "processNotes": "Two-part coding + system design.",
      "difficulty": 4,
      "entryCount": 5
    }
  ]
}
```
**Errors:** `404 RESOURCE_NOT_FOUND` (no published intel for that company).

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `IntelController` | `com.careercopilot.intel` | Public browse endpoints |
| `IntelService` | `com.careercopilot.intel` | Aggregate published rows for browse |
| `ContributionService` | `com.careercopilot.intel` | Orchestrates contribute → anonymize → stage/publish |
| `AnonymizationService` | `com.careercopilot.intel` | PII scrubbing; role normalization; threshold gate; **sole writer** of intel table |
| `CompanyIntelRepository` | `com.careercopilot.intel` | Reads (published) + writes (via AnonymizationService only) |
| `PiiScrubber` | `com.careercopilot.intel` | Regex/NER-style scrub of free text |
| DTOs | `com.careercopilot.intel.dto` | `CompanyIntelSummary`, `CompanyIntelDetail`, `ContributeResponse` |

### Interaction Flow

```
POST …/interview-logs/{logId}/contribute
  → TrackerController delegates → ContributionService.contribute(userId, applicationId, logId)
    → load interview_log (owner check → 403 / 404)
    → if contributed_at != null → 409 ALREADY_CONTRIBUTED
    → AnonymizationService.anonymize(log):
        · PiiScrubber.scrub(notes, questions, feedback)   -- strip names/emails/phones/urls
        · normalize company + role_category
        · build company_interview_intel row (no user_id) — STAGED, staging_batch_id per company
    → mark log.contributed_at = now(), contribution_status = STAGED
    → if published+staged count for company ≥ threshold:
        · flip that company's staged rows → is_published = true, published_at = now(), staging_batch_id = null
        · mark corresponding logs contribution_status = PUBLISHED
  → returns ContributeResponse (200, STAGED or PUBLISHED)

GET /intel/companies/{companyName}
  → IntelController → IntelService.byCompany(name)   -- WHERE is_published = true only
```

---

## LLM Tasks

Optional. The anonymization pipeline is primarily deterministic (regex/dictionary scrubbing). An
**optional** `EXTRACTION`-tier LLM assist may generalize free-text process notes (e.g., paraphrase to
remove residual identifiers). If used:

| Field | Value |
|---|---|
| Model Tier | EXTRACTION |
| LlmTask enum | `LlmTask.EXTRACTION` |
| Guardrails | PiiRedactionAdvisor (mandatory), JsonSchemaValidator; PromptInjection N/A (own-user text) |

The deterministic `PiiScrubber` is authoritative and always runs; the LLM assist may only remove, never
add, content. If the LLM is disabled by config, contribution still works.

---

## Rendering Tasks

N/A for this phase.

---

## Test Plan

### Unit Tests

| Test class | What it tests |
|---|---|
| `PiiScrubberTest` | Emails, phones, full names, URLs, addresses removed from sample notes/questions |
| `AnonymizationServiceTest` | Output row has no user id; role normalized; STAGED below threshold |
| `ContributionServiceTest` | Threshold flip publishes staged rows; second contribute → 409 |
| `IntelServiceTest` | Aggregates only published rows; hidden while staged |

### Integration Tests

| Test class | What it tests |
|---|---|
| `ContributionIntegrationTest` | Contribute stages, then publishes at N=3; source log flags updated |
| `IntelBrowseIntegrationTest` | Public `GET /intel/*` needs no auth; returns only published aggregates |
| `IntelPrivacyIntegrationTest` | No endpoint/response exposes user_id, email, or raw private notes |

### Guardrail / Privacy Tests

| Test | Scenario |
|---|---|
| `AnonymizationPiiLeakTest` | A crafted log with embedded PII produces an intel row with none of it |
| `IntelNoUserLeakTest` | Company detail JSON contains no identity/private field under any input |

---

## Deploy Steps

### Pre-deploy Checklist
- [ ] `./mvnw test` green; `./mvnw spotless:check` clean
- [ ] V13 migration reviewed (additive; shared table, no PII columns)
- [ ] Aggregation threshold (N) set in config
- [ ] Anonymization/PII tests reviewed and green
- [ ] Confirmed only `AnonymizationService` writes the intel table

### Environment Variables Required

| Variable | Description | Example |
|---|---|---|
| `INTEL_MIN_AGGREGATION` | Min anonymized entries before publish | `3` |
| `INTEL_LLM_ASSIST_ENABLED` | Toggle optional LLM note generalization | `false` |

### DigitalOcean Deploy Steps
1. Push to `main`; green CI.
2. DO auto-deploys; Flyway applies V13.
3. Smoke: contribute 3 logs for one company as test users → intel publishes; browse it unauthenticated;
   verify no private fields present.

---

## Definition of Done

- [ ] Spec status set to APPROVED before implementation begins
- [ ] `contribute` fully implemented: anonymize → stage → publish at threshold
- [ ] `company_interview_intel` has no user/application columns and is written only by `AnonymizationService`
- [ ] Public browse endpoints work without auth and expose only published aggregates
- [ ] PII scrubbing verified by tests (emails/phones/names/URLs removed)
- [ ] Second contribution of a log returns 409; staged intel stays hidden below N
- [ ] V13 Flyway migration applies cleanly
- [ ] Unit, integration, and privacy tests pass with Testcontainers
- [ ] React public intel page + tracker contribute toggle work against the live API
- [ ] `/review` checklist passes
- [ ] CLAUDE.md updated to Phase 7
- [ ] Deployed to DigitalOcean and smoke-tested
- [ ] Spec status set to IMPLEMENTED
