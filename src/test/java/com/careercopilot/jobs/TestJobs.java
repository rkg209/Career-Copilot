package com.careercopilot.jobs;

import java.time.Instant;
import java.util.List;

/**
 * Public factory so tests outside this package (e.g. tailoring) can build {@link JobPosting}
 * entities despite the protected constructor.
 */
public final class TestJobs {

  private TestJobs() {}

  public static JobPosting jobPosting(String title, String company, String fullText) {
    JobPosting job =
        JobTestFixtures.entity(JobSource.MANUAL_PASTE, "test-" + title.hashCode(), title, company);
    job.setFullText(fullText);
    job.setSkills(List.of("Java", "Spring Boot"));
    job.setResponsibilities(List.of("Build backend services"));
    job.setIngestedAt(Instant.now());
    return job;
  }
}
