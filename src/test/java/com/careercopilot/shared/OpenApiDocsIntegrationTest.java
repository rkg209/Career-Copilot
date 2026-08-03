package com.careercopilot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

/**
 * The "docs match deployed endpoints" guarantee: every controller's paths must show up in the
 * generated OpenAPI document, so a controller added without docs fails this test loudly.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiDocsIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;

  private static final List<String> EXPECTED_PATHS =
      List.of(
          // AuthController
          "/api/v1/auth/register",
          "/api/v1/auth/login",
          "/api/v1/auth/me",
          // JobController
          "/api/v1/jobs/roles",
          "/api/v1/jobs/for-me",
          "/api/v1/jobs/ingestion-status",
          "/api/v1/jobs/search",
          "/api/v1/jobs/{id}",
          "/api/v1/jobs/paste",
          // ProfileController
          "/api/v1/profile/experience",
          "/api/v1/profile/experience/{id}",
          "/api/v1/profile/education",
          "/api/v1/profile/education/{id}",
          "/api/v1/profile/skill-groups",
          "/api/v1/profile/highlights",
          "/api/v1/profile/highlights/{id}",
          // ScoringController
          "/api/v1/scoring/score",
          "/api/v1/scoring/results/{resumeVersionId}",
          // TailoringController
          "/api/v1/tailoring/tailor",
          "/api/v1/tailoring/resumes",
          "/api/v1/tailoring/resumes/{id}",
          "/api/v1/tailoring/resumes/{id}/pdf",
          "/api/v1/tailoring/cover-letter",
          "/api/v1/tailoring/cover-letters/{id}/pdf",
          "/api/v1/tailoring/templates",
          // TrackerController
          "/api/v1/tracker/applications",
          "/api/v1/tracker/applications/{id}",
          "/api/v1/tracker/applications/{id}/interview-logs",
          "/api/v1/tracker/applications/{id}/interview-logs/{logId}/contribute",
          // IntelController
          "/api/v1/intel/companies",
          "/api/v1/intel/companies/{companyName}",
          // TrainerController
          "/api/v1/trainer/generate",
          "/api/v1/trainer/questions/{id}/answer",
          "/api/v1/trainer/review/due",
          "/api/v1/trainer/questions",
          // MetricsController (Phase 8)
          "/api/v1/metrics/ats-improvement",
          "/api/v1/metrics/tokens");

  @Test
  void docsAreServedUnauthenticated_andListEveryControllerPath() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/docs", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode root = new ObjectMapper().readTree(response.getBody());
    JsonNode paths = root.get("paths");
    assertThat(paths).isNotNull();

    List<String> missing = EXPECTED_PATHS.stream().filter(p -> !paths.has(p)).toList();
    assertThat(missing).as("paths missing from generated OpenAPI doc").isEmpty();
  }
}
