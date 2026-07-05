package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercopilot.jobs.source.AshbyJobSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Persists ASHBY-sourced postings through the real ingestion service against Testcontainers,
 * exercising the new enum value. The adapter is backed by a mock HTTP server — no live network.
 */
@Testcontainers
@SpringBootTest
class AshbyIngestionIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private JobIngestionService ingestionService;
  @Autowired private JobPostingRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  @Test
  void ingestsAshbyBoard_persistsWithNewEnumValue() {
    String body =
        """
        {"jobs": [
          {"id": "abc", "title": "Machine Learning Engineer", "location": "Bengaluru",
           "isRemote": true, "descriptionPlain": "Python, PyTorch, and Kubernetes.",
           "jobUrl": "https://ashby.test/acme/abc", "organizationName": "Acme Inc",
           "publishedAt": "2026-06-25T00:00:00Z"}
        ]}
        """;
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(containsString("/posting-api/job-board/acme")))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    AshbyJobSource adapter =
        new AshbyJobSource(builder, "https://ashby.test", java.util.List.of("acme"));

    JobIngestionService.IngestionResult result = ingestionService.ingestFrom(adapter);

    assertThat(result.inserted()).isEqualTo(1);
    JobPosting saved =
        repository.findBySourceAndExternalId(JobSource.ASHBY, "acme:abc").orElseThrow();
    assertThat(saved.getSource()).isEqualTo(JobSource.ASHBY);
    assertThat(saved.getTitle()).isEqualTo("Machine Learning Engineer");
    assertThat(saved.getCompany()).isEqualTo("Acme Inc");
    assertThat(saved.getSkills()).contains("python", "pytorch", "kubernetes");
  }
}
