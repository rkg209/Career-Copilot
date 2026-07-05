package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.jobs.source.JobSourceAdapter;
import com.careercopilot.jobs.source.NormalizedJob;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies the freshness sweep deactivates stale postings and re-ingestion reactivates them. */
@Testcontainers
@SpringBootTest
class JobFreshnessIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private JobIngestionService ingestionService;
  @Autowired private JobPostingRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private NormalizedJob job(String extId, Instant postedAt) {
    return new NormalizedJob(
        JobSource.REMOTEOK,
        extId,
        "Engineer",
        "Acme",
        "Remote",
        true,
        null,
        null,
        null,
        null,
        List.of("Java"),
        List.of(),
        "full text",
        "https://x/" + extId,
        postedAt,
        null,
        null);
  }

  @Test
  void deactivatesStale_keepsFresh_andReactivatesOnReingest() {
    Instant stale = Instant.now().minus(60, ChronoUnit.DAYS);
    Instant fresh = Instant.now().minus(5, ChronoUnit.DAYS);
    repository.save(JobPostingMapper.toEntity(job("old", stale)));
    repository.save(JobPostingMapper.toEntity(job("new", fresh)));

    int deactivated = ingestionService.deactivateStale();

    assertThat(deactivated).isEqualTo(1);
    assertThat(
            repository
                .findBySourceAndExternalId(JobSource.REMOTEOK, "old")
                .orElseThrow()
                .isActive())
        .isFalse();
    assertThat(
            repository
                .findBySourceAndExternalId(JobSource.REMOTEOK, "new")
                .orElseThrow()
                .isActive())
        .isTrue();
    assertThat(repository.findByActiveTrue(org.springframework.data.domain.Pageable.unpaged()))
        .hasSize(1);

    // Re-ingesting the stale posting (now with a recent posted date) reactivates it.
    JobSourceAdapter adapter = stub(List.of(job("old", Instant.now())));
    ingestionService.ingestFrom(adapter);

    assertThat(
            repository
                .findBySourceAndExternalId(JobSource.REMOTEOK, "old")
                .orElseThrow()
                .isActive())
        .isTrue();
  }

  private JobSourceAdapter stub(List<NormalizedJob> jobs) {
    return new JobSourceAdapter() {
      @Override
      public JobSource id() {
        return JobSource.REMOTEOK;
      }

      @Override
      public List<NormalizedJob> fetch() {
        return jobs;
      }
    };
  }
}
