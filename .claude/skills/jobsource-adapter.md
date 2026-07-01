# Skill: JobSource Adapter

## Overview

`JobSource` is the interface all job ingestion adapters implement. Each adapter fetches job postings from one source (LinkedIn scraper, Indeed API, direct company ATS, manual import, etc.) and normalizes them to the internal `JobPosting` schema.

---

## The JobSource Interface

```java
// com.careercopilot.jobs.source.JobSource
public interface JobSource {

    /**
     * Unique identifier for this source. Used in DB and config.
     * Examples: "linkedin", "indeed", "greenhouse", "manual"
     */
    String sourceId();

    /**
     * Human-readable name for display.
     */
    String displayName();

    /**
     * Fetch new job postings since the given timestamp.
     * Implementations MUST respect rate limits.
     * MUST NOT return duplicates (check by externalId).
     *
     * @param since fetch postings updated after this time; null = fetch all
     * @return list of normalized job postings (may be empty, never null)
     */
    List<RawJobPosting> fetchSince(@Nullable Instant since);

    /**
     * Whether this source is currently active (config-driven).
     */
    boolean isEnabled();
}
```

---

## Rate-Limit Requirements

Every adapter MUST implement rate limiting. Non-negotiable.

```java
// Use RateLimiter from careercopilot.shared.RateLimiterRegistry
// Configured in application.yml:
// careercopilot.job-sources.{sourceId}.requests-per-minute: 10

@Component
public class LinkedInJobSource implements JobSource {
    private final RateLimiter rateLimiter;

    public LinkedInJobSource(RateLimiterRegistry registry) {
        this.rateLimiter = registry.getOrCreate("linkedin", 10); // 10 RPM default
    }

    @Override
    public List<RawJobPosting> fetchSince(Instant since) {
        rateLimiter.acquire();  // blocks if rate exceeded
        // ... fetch logic
    }
}
```

Minimum delay between requests: configurable via `careercopilot.job-sources.{sourceId}.delay-ms` (default: 1000ms).

---

## ToS Compliance Check

Before implementing a new scraper, check the source's Terms of Service:

1. Read `robots.txt` at the source domain.
2. Check for an explicit API (prefer API over scraping always).
3. Confirm no ToS clause prohibits automated access for job aggregation.
4. Document your ToS review decision in the adapter's class-level Javadoc:

```java
/**
 * JobSource adapter for Greenhouse ATS.
 * ToS Review: Greenhouse public job board API is documented and open for use.
 * No ToS restrictions found as of 2025-01. Rate limit: 10 req/s.
 * API docs: https://developers.greenhouse.io/job-board.html
 */
@Component
public class GreenhouseJobSource implements JobSource { ... }
```

If ToS is unclear → do not implement the scraper. Use manual import instead.

---

## Internal JobPosting Schema (Normalized)

All adapters must produce `RawJobPosting` objects. The ingestion pipeline then converts these to JPA `JobPosting` entities.

```java
// com.careercopilot.jobs.source.RawJobPosting
public record RawJobPosting(
    String externalId,              // source-specific ID; used for dedup
    String sourceId,                // which adapter produced this
    String title,                   // job title
    String company,                 // company name (display)
    String companyDomain,           // e.g., "stripe.com" (for dedup/linking)
    String location,                // city, state, remote, etc.
    EmploymentType employmentType,  // FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP
    String experienceLevel,         // "Senior", "Staff", "L5", etc.
    List<String> requiredSkills,    // extracted required skills
    List<String> preferredSkills,   // extracted preferred skills
    List<String> responsibilities,  // job responsibilities
    String fullText,                // complete raw JD text (for RAG/scoring)
    String applyUrl,                // link to apply
    Instant postedAt,               // when the job was posted
    Instant fetchedAt               // when we fetched it (set by framework)
) {}
```

### EmploymentType enum

```java
public enum EmploymentType {
    FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP, FREELANCE, OTHER
}
```

---

## Normalization Rules

Adapters should normalize on the way out:

- `title`: title-case, trim whitespace.
- `company`: use official name if known (e.g., "Meta" not "Facebook, Inc.").
- `location`: normalize "Remote" (case-insensitive variants) → "Remote".
- `requiredSkills` / `preferredSkills`: if the source doesn't distinguish, put all in `requiredSkills` and leave `preferredSkills` empty. Skills should be individual tokens ("Python", "Kubernetes") not phrases.
- `fullText`: include the complete unmodified JD text. This feeds the LLM and the ATS scorer.
- `externalId`: must be stable — the same posting fetched twice should produce the same `externalId`. Use the source's native ID if available; otherwise hash `(sourceId + company + title + postedAt)`.

---

## How to Register a New Adapter

1. Create `com.careercopilot.jobs.source.{Name}JobSource` implementing `JobSource`.
2. Annotate with `@Component` so Spring picks it up.
3. Add config block in `application.yml`:
   ```yaml
   careercopilot:
     job-sources:
       {sourceId}:
         enabled: true
         requests-per-minute: 10
         delay-ms: 1000
   ```
4. The `JobIngestionService` auto-discovers all `JobSource` beans via Spring injection:
   ```java
   @Service
   public class JobIngestionService {
       private final List<JobSource> sources;  // Spring injects all beans
       // ...
   }
   ```
5. Write an integration test in `{Name}JobSourceIntegrationTest`:
   - Mock the HTTP layer (WireMock).
   - Assert `fetchSince()` returns correctly normalized `RawJobPosting` objects.
   - Assert deduplication works (same externalId = not re-ingested).
   - Assert rate limiting is applied.

---

## Ingestion Pipeline Flow

```
JobIngestionScheduler (@Scheduled, every 6h)
  → for each enabled JobSource:
    → source.fetchSince(lastFetchTime)
    → for each RawJobPosting:
      → JobPostingRepository.existsBySourceIdAndExternalId() → skip if exists
      → JobPostingMapper.toEntity(raw) → JobPosting entity
      → JobPostingRepository.save(entity)
      → EmbeddingService.embedJobPosting(entity)  // embed fullText for search
      → publish JobPostingIngestedEvent (for async notifications)
    → update lastFetchTime for this source
```

---

## Deduplication

Unique constraint on `(source_id, external_id)` in the `job_postings` table. Any attempt to insert a duplicate raises a DB constraint error — catch `DataIntegrityViolationException` and skip silently (log at DEBUG level).
