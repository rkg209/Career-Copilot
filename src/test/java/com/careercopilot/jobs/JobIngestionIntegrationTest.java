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

@Testcontainers
@SpringBootTest
class JobIngestionIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private JobIngestionService ingestionService;
  @Autowired private JobPostingRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  @Test
  void reingestingSameSource_producesNoDuplicates() {
    JobSourceAdapter adapter =
        stub(
            List.of(
                JobTestFixtures.normalized(JobSource.REMOTEOK, "1", "Engineer", "Acme"),
                JobTestFixtures.normalized(JobSource.REMOTEOK, "2", "SRE", "Globex")));

    JobIngestionService.IngestionResult first = ingestionService.ingestFrom(adapter);
    assertThat(first.inserted()).isEqualTo(2);
    assertThat(repository.count()).isEqualTo(2);

    JobIngestionService.IngestionResult second = ingestionService.ingestFrom(adapter);
    assertThat(second.inserted()).isZero();
    assertThat(second.updated()).isEqualTo(2);
    assertThat(repository.count()).isEqualTo(2);
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
