package com.careercopilot.jobs;

import com.careercopilot.jobs.source.NormalizedJob;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Shared builders for job integration tests (same package → can use the package-private mapper).
 */
final class JobTestFixtures {

  private JobTestFixtures() {}

  static NormalizedJob normalized(
      JobSource source, String externalId, String title, String company) {
    return new NormalizedJob(
        source,
        externalId,
        title,
        company,
        "Remote",
        true,
        "full_time",
        BigDecimal.valueOf(120000),
        BigDecimal.valueOf(160000),
        "USD",
        List.of("Java", "Spring"),
        List.of("Own the backend"),
        title + " at " + company + " building distributed systems",
        "https://example.test/" + externalId,
        Instant.parse("2026-06-20T00:00:00Z"),
        null,
        null);
  }

  static JobPosting entity(JobSource source, String externalId, String title, String company) {
    return JobPostingMapper.toEntity(normalized(source, externalId, title, company));
  }
}
