package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "jobs.roles.quant.label=Quant / Trading",
      "jobs.roles.quant.keywords=quant,trading systems",
      "jobs.roles.ai-ml-engineer.label=AI / ML Engineer",
      "jobs.roles.ai-ml-engineer.keywords=machine learning"
    })
class JobRoleFilterIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JobPostingRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private void seed(String extId, String title, List<String> roles) {
    JobPosting p = JobTestFixtures.entity(JobSource.REMOTEOK, extId, title, "Acme");
    p.setMatchedRoles(new java.util.ArrayList<>(roles));
    repository.save(p);
  }

  @Test
  void list_filtersByRole_viaContainment() {
    seed("1", "Quant Developer", List.of("quant"));
    seed("2", "ML Engineer", List.of("ai-ml-engineer"));
    seed("3", "ML Quant", List.of("ai-ml-engineer", "quant"));

    ResponseEntity<JsonNode> quant =
        restTemplate.getForEntity("/api/v1/jobs?role=quant", JsonNode.class);

    assertThat(quant.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(quant.getBody().get("content")).hasSize(2); // rows 1 and 3
  }

  @Test
  void search_filtersByRole() {
    seed("1", "Quant Developer", List.of("quant"));
    seed("2", "ML Engineer", List.of("ai-ml-engineer"));

    ResponseEntity<JsonNode> res =
        restTemplate.getForEntity("/api/v1/jobs/search?role=ai-ml-engineer", JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().get("content")).hasSize(1);
    assertThat(res.getBody().get("content").get(0).get("matchedRoles").get(0).asText())
        .isEqualTo("ai-ml-engineer");
  }

  @Test
  void unknownRole_returns400() {
    ResponseEntity<String> res =
        restTemplate.getForEntity("/api/v1/jobs?role=nonsense", String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void response_exposesIngestedAtRecency() {
    seed("1", "Quant Developer", List.of("quant"));

    ResponseEntity<JsonNode> res =
        restTemplate.getForEntity("/api/v1/jobs?role=quant", JsonNode.class);

    assertThat(res.getBody().get("content").get(0).hasNonNull("ingestedAt")).isTrue();
  }
}
