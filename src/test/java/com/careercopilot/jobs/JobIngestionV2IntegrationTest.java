package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.jobs.source.JobSourceAdapter;
import com.careercopilot.jobs.source.NormalizedJob;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies the v2 ingestion behavior end-to-end: relevance filtering + skill extraction. */
@Testcontainers
@SpringBootTest(
    properties = {
      "jobs.targets.title-keywords=machine learning,software engineer",
      "jobs.targets.location-terms="
    })
class JobIngestionV2IntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private JobIngestionService ingestionService;
  @Autowired private JobPostingRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private NormalizedJob job(String extId, String title, List<String> skills, String fullText) {
    return new NormalizedJob(
        JobSource.GREENHOUSE,
        extId,
        title,
        "Acme",
        "Remote",
        true,
        null,
        null,
        null,
        null,
        skills,
        List.of(),
        fullText,
        "https://x/" + extId,
        null,
        null,
        null);
  }

  @Test
  void filtersOffTargetAndExtractsSkills() {
    JobSourceAdapter adapter =
        stub(
            List.of(
                job("1", "Machine Learning Engineer", List.of(), "Work with Python and PyTorch."),
                job("2", "Software Engineer", List.of("Java"), "Backend services."),
                job("3", "Product Designer", List.of(), "Design delightful experiences.")));

    JobIngestionService.IngestionResult result = ingestionService.ingestFrom(adapter);

    assertThat(result.fetched()).isEqualTo(3);
    assertThat(result.filteredOut()).isEqualTo(1);
    assertThat(result.kept()).isEqualTo(2);
    assertThat(repository.count()).isEqualTo(2);

    JobPosting ml = repository.findBySourceAndExternalId(JobSource.GREENHOUSE, "1").orElseThrow();
    assertThat(ml.getSkills()).contains("python", "pytorch"); // extracted from description
    JobPosting swe = repository.findBySourceAndExternalId(JobSource.GREENHOUSE, "2").orElseThrow();
    assertThat(swe.getSkills()).containsExactly("Java"); // source-provided skills untouched
    assertThat(repository.findBySourceAndExternalId(JobSource.GREENHOUSE, "3")).isEmpty();
  }

  private JobSourceAdapter stub(List<NormalizedJob> jobs) {
    return new JobSourceAdapter() {
      @Override
      public JobSource id() {
        return JobSource.GREENHOUSE;
      }

      @Override
      public List<NormalizedJob> fetch() {
        return jobs;
      }
    };
  }
}
