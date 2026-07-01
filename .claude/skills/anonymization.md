# Skill: Anonymization for Company Interview Intel

## Overview

`company_interview_intel` is the only table shared across users. Before any entry is written to this table it must pass through `AnonymizationService`. This skill defines what gets stripped, how free-text is scrubbed, and when aggregated intel is surfaced to other users.

---

## What Gets Stripped (Exact Fields)

These fields are **never written** to `company_interview_intel`:

| Field | Why |
|---|---|
| `userId` | Direct user identifier |
| `applicationId` | Linkable back to user's application record |
| `userEmail` | PII |
| `userName` | PII |
| `applicationDate` | High-precision date narrows down identity |
| `interviewDate` | Same reason |
| `interviewerName` | Could identify interviewer and indirectly user |
| `offerSalary` | Sensitive; could identify individual |

Fields that ARE written (anonymized):
- `companyId` (foreign key to companies table — shared, public)
- `roleLevel` (e.g., "Senior", "Staff", "L5" — general level only)
- `interviewRound` (Phone Screen, Technical, System Design, etc.)
- `questionText` (scrubbed — see below)
- `questionCategory` (Algorithms, System Design, Behavioral, etc.)
- `difficulty` (Easy / Medium / Hard)
- `outcome` (PASSED / FAILED / NO_DECISION)
- `notes` (scrubbed free text)
- `createdAt` (truncated to week-of-year — not exact date)

---

## Free-Text Scrubbing

Apply `PiiRedactionService.scrub(text)` to all free-text fields before writing:
- `questionText`
- `notes`
- Any other `TEXT` column

`PiiRedactionService.scrub()`:
1. Apply all PII regex patterns from `PiiRedactionAdvisor` (email, phone, SSN, address, etc.).
2. Replace proper names matching a name dictionary with `[PERSON]`.
3. Replace company names (other than the target company) with `[COMPANY]`.
4. Truncate result to 2000 chars max.

---

## Minimum Aggregation Threshold

Intel for a company is **not surfaced to other users** until at least N anonymized entries exist for that company+role-level combination.

```yaml
careercopilot:
  intel:
    min-aggregation-threshold: 3    # configurable, default 3
```

### Staging table for below-threshold entries

Entries below the threshold are written to `company_intel_staging` (same schema as `company_interview_intel` but not exposed via API). A scheduled job promotes staging entries to the live table once the threshold is met:

```java
@Scheduled(cron = "0 0 * * * *")  // hourly
public void promoteQualifiedStagingEntries() {
    List<CompanyRoleGroup> groups = stagingRepo.findGroupsAboveThreshold(minThreshold);
    for (CompanyRoleGroup group : groups) {
        List<IntelStagingEntry> entries = stagingRepo.findByCompanyAndRole(group);
        intelRepo.saveAll(entries.stream().map(this::toLiveEntry).toList());
        stagingRepo.deleteAll(entries);
    }
}
```

---

## Anonymization Service API

```java
// com.careercopilot.intel.AnonymizationService
@Service
public class AnonymizationService {

    /**
     * Convert a raw InterviewRecord (linked to a user) into an anonymous IntelEntry.
     * Strips all user-identifying fields, scrubs free text.
     * @throws AnonymizationException if required fields are missing or scrubbing fails
     */
    public IntelStagingEntry anonymize(InterviewRecord record);

    /**
     * Check if an entry is ready to be promoted from staging to live.
     * Returns true if the company+roleLevel group now has >= minThreshold entries.
     */
    public boolean meetsAggregationThreshold(UUID companyId, String roleLevel);
}
```

---

## Data Flow

```
User submits interview notes (TrackerController)
  → TrackerService.recordInterviewOutcome(userId, interviewRecordDto)
    → InterviewRecord saved in user's private tracker
    → AnonymizationService.anonymize(record) → IntelStagingEntry
    → IntelStagingRepository.save(stagingEntry)
    → AnonymizationService.meetsAggregationThreshold(companyId, roleLevel)
      → if true: promote to company_interview_intel immediately
      → if false: stays in staging
```

---

## Intel Query — Never Leak User Data

When other users query company intel:

```java
// com.careercopilot.intel.IntelRepository
// ONLY query company_interview_intel, NEVER company_intel_staging
List<IntelEntry> findByCompanyIdAndRoleLevel(UUID companyId, String roleLevel);
```

The API response for intel must NEVER include any field that was in the "stripped" list above. The `IntelEntryDto` must only contain:
- `companyName`
- `roleLevel`
- `interviewRound`
- `questionText` (scrubbed)
- `questionCategory`
- `difficulty`
- `outcome`
- `weekOfYear` (not exact date)

---

## Schema

```sql
-- company_interview_intel (live, shared)
CREATE TABLE company_interview_intel (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       UUID NOT NULL REFERENCES companies(id),
    role_level       VARCHAR(50),
    interview_round  VARCHAR(100),
    question_text    TEXT,
    question_category VARCHAR(100),
    difficulty       VARCHAR(20),
    outcome          VARCHAR(20),
    notes            TEXT,
    week_of_year     INT,            -- e.g., 202412 (YYYY + ISO week)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
    -- NO user_id, NO application_id, NO email, NO name, NO exact date
);

-- company_intel_staging (below threshold, not exposed via API)
CREATE TABLE company_intel_staging (
    LIKE company_interview_intel INCLUDING ALL,
    source_company_id UUID NOT NULL   -- for threshold grouping
);
```

---

## Testing Anonymization

- `AnonymizationServiceTest`: given a `InterviewRecord` with PII fields → assert all stripped fields absent from output, free-text scrubbed.
- `ThresholdPromotionTest`: insert N-1 staging entries → assert not promoted; insert Nth → assert promoted to live table.
- `IntelApiIntegrationTest`: assert `GET /api/v1/intel/company/{id}` never returns userId, email, applicationId, or exact dates.
