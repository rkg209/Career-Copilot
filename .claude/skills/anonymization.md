# Skill: Anonymization for Company Interview Intel

## Overview

`company_interview_intel` is the only table shared across users. Before any entry is written to
this table it must pass through `AnonymizationService`. This skill defines what gets stripped,
how free-text is scrubbed, and when aggregated intel is surfaced to other users. Design is
authoritative per `specs/phase-6-company-intel.md` (single-table, flip-on-write — no separate
`companies` table, no `company_intel_staging` table, no scheduled promotion job).

---

## What Gets Stripped (Exact Fields)

These fields are **never written** to `company_interview_intel`:

| Field | Why |
|---|---|
| `userId` | Direct user identifier |
| `applicationId` | Linkable back to user's application record |
| `userEmail` / `userName` | PII |
| `interviewDate` | High-precision date narrows down identity |
| `interviewerRole` | Could indirectly identify the interviewer/user if too specific |

Fields that ARE written (anonymized), matching the `company_interview_intel` columns:
- `company` (TEXT — normalized company name, shared/public)
- `role_category` (normalized role family, e.g. "Software Engineer" — not a verbatim job title)
- `round_type` (reuses the `interview_round_type` enum from V14: PHONE_SCREEN, TECHNICAL, etc.)
- `questions` (TEXT[] — scrubbed, see below)
- `process_notes` (scrubbed free text, from the log's `notes`/`feedback_received`)
- `difficulty` (SMALLINT 1–5, carried through as-is — not identifying)
- `is_published` / `staging_batch_id` / `published_at` — aggregation-gate bookkeeping, not derived
  from user data

---

## Free-Text Scrubbing

Apply `PiiScrubber.scrub(text)` to all free-text fields before writing:
- each entry in `questions`
- `process_notes`

`PiiScrubber.scrub()`:
1. Apply PII regex patterns (email, phone, SSN, address, URLs) — reuse the same pattern set as
   `shared/guardrail/PiiRedactionAdvisor` where possible.
2. Replace proper names matching a name dictionary with `[PERSON]`.
3. Replace company names other than the target company with `[COMPANY]`.
4. Truncate result to 2000 chars max.

The `PiiScrubber` is deterministic and authoritative. An **optional** `EXTRACTION`-tier LLM assist
(config `INTEL_LLM_ASSIST_ENABLED`, default `false`) may further paraphrase `process_notes` to
remove residual identifiers, but it may only remove content, never add it, and contribution must
work correctly with the assist disabled.

---

## Minimum Aggregation Threshold

Intel for a company is **not surfaced to other users** until at least N anonymized entries exist
for that company (checked synchronously on every contribution — no batch/cron job).

```yaml
careercopilot:
  intel:
    min-aggregation-threshold: 3    # configurable via INTEL_MIN_AGGREGATION, default 3
```

### Staging via `staging_batch_id`, not a separate table

Below-threshold rows are written directly to `company_interview_intel` with
`is_published = false` and a shared `staging_batch_id` (grouping all currently-staged rows for
that company). There is no separate staging table. When a contribution pushes a company's
staged+published count to ≥ threshold, `ContributionService` flips that company's staged batch in
place: `is_published = true`, `published_at = now()`, `staging_batch_id = null`, and marks the
corresponding source `interview_logs` rows `contribution_status = PUBLISHED`.

---

## Anonymization Service API

```java
// com.careercopilot.intel.AnonymizationService
@Service
public class AnonymizationService {

    /**
     * Convert a private InterviewLog (+ its Application, for company/title) into an anonymized
     * CompanyIntelEntry. Strips all user-identifying fields, scrubs free text, normalizes role.
     * Sole writer of company_interview_intel.
     */
    public CompanyIntelEntry anonymize(InterviewLog log, Application application);
}
```

Threshold evaluation and the staged→published flip live in `ContributionService`, which calls
`AnonymizationService.anonymize()` and then queries `CompanyIntelRepository` for the current
staged+published count for that company.

---

## Data Flow

```
POST …/interview-logs/{logId}/contribute (TrackerController → ContributionService)
  → load InterviewLog + Application (owner check → 403/404)
  → if log.contributedAt != null → 409 ALREADY_CONTRIBUTED
  → AnonymizationService.anonymize(log, application) → CompanyIntelEntry (is_published=false)
  → CompanyIntelRepository.save(entry)
  → mark log.contributedAt = now(), contributionStatus = STAGED
  → count = CompanyIntelRepository.countByCompany(company)  -- staged + published
  → if count >= threshold: flip that company's staged batch → is_published=true,
      published_at=now(), staging_batch_id=null; flip source logs → PUBLISHED
  → return ContributeResponse(status, company, publishedCountForCompany, threshold)
```

---

## Intel Query — Never Leak User Data

Public browse reads must only ever query published rows:

```java
// com.careercopilot.intel.CompanyIntelRepository
List<CompanyIntelEntry> findByCompanyAndIsPublishedTrue(String company);
```

The API response for intel (`CompanyIntelSummary`, `CompanyIntelDetail`) must NEVER include any
field from the "stripped" list above, and must only ever be built from rows where
`is_published = true`.

---

## Schema

```sql
-- company_interview_intel (V15, shared, live + staged in one table)
CREATE TABLE company_interview_intel (
    id               BIGSERIAL            PRIMARY KEY,
    company          TEXT                 NOT NULL,
    role_category    TEXT,
    round_type       interview_round_type NOT NULL,
    questions        TEXT[]               NOT NULL DEFAULT '{}',
    process_notes    TEXT,
    difficulty       SMALLINT             CHECK (difficulty BETWEEN 1 AND 5),
    is_published     BOOLEAN              NOT NULL DEFAULT false,
    staging_batch_id UUID,
    published_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ          NOT NULL DEFAULT now()
    -- NO user_id, NO application_id, NO email, NO name, NO exact interview date
);
```

See `specs/phase-6-company-intel.md` for the full DDL including indexes.

---

## Testing Anonymization

- `PiiScrubberTest`: emails, phones, full names, URLs removed from sample notes/questions.
- `AnonymizationServiceTest`: output entry has no user/application id; role normalized; row is
  unpublished (staged) when below threshold.
- `ContributionServiceTest`: Nth contribution for a company flips the staged batch to published;
  contributing an already-contributed log returns 409.
- `AnonymizationPiiLeakTest` / `IntelNoUserLeakTest`: a crafted log with embedded PII produces an
  intel row/API response with none of it, under any input.
