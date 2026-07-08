package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercopilot.jobs.source.AdzunaJobSource;
import com.careercopilot.jobs.source.JobDescriptionEnricher;
import com.careercopilot.jobs.source.JobRelevanceFilter;
import com.careercopilot.jobs.source.RoleTagger;
import com.careercopilot.jobs.source.SkillExtractor;
import java.util.List;
import java.util.Set;
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
 * Drives the real {@link AdzunaJobSource} (backed by a mock HTTP server) through the ingestion
 * service against Testcontainers — proving the query-driven aggregator persists role-tagged rows
 * with India locations. No live network.
 */
@Testcontainers
@SpringBootTest(
    properties = {
      "jobs.search-locations=India",
      "jobs.roles.ai-ml-engineer.label=AI / ML Engineer",
      "jobs.roles.ai-ml-engineer.keywords=machine learning,ai engineer",
      "jobs.enrichment.enabled=false"
    })
class AdzunaQueryIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private JobPostingRepository repository;
  @Autowired private JobRelevanceFilter relevanceFilter;
  @Autowired private SkillExtractor skillExtractor;
  @Autowired private JobDescriptionEnricher disabledEnricher;
  @Autowired private RoleTagger roleTagger;
  @Autowired private RoleProperties roleProperties;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  @Test
  void ingestsAdzunaForRole_persistsTaggedIndiaRows() {
    String body =
        """
        {"results": [
          {"id": "a1", "title": "AI Engineer", "company": {"display_name": "Acme India"},
           "location": {"display_name": "Bengaluru", "area": ["India", "Karnataka", "Bengaluru"]},
           "description": "Build ML systems with Python.", "redirect_url": "https://adzuna.test/a1",
           "created": "2026-06-25T00:00:00Z"}
        ]}
        """;
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(containsString("/v1/api/jobs/in/search/1")))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    AdzunaJobSource adzuna =
        new AdzunaJobSource(
            builder, "https://adzuna.test", "app-id", "app-key", "in", 50, 1, "INR");

    JobIngestionService service =
        new JobIngestionService(
            List.of(),
            List.of(adzuna),
            repository,
            relevanceFilter,
            skillExtractor,
            disabledEnricher,
            roleTagger,
            roleProperties,
            Set.of(JobSource.ADZUNA),
            45);

    service.ingestFromQueryableSources();

    JobPosting saved = repository.findBySourceAndExternalId(JobSource.ADZUNA, "a1").orElseThrow();
    assertThat(saved.getSource()).isEqualTo(JobSource.ADZUNA);
    assertThat(saved.getLocation()).contains("India");
    assertThat(saved.getMatchedRoles()).containsExactly("ai-ml-engineer");
    assertThat(saved.getSkills()).contains("python"); // extracted from description
  }
}
