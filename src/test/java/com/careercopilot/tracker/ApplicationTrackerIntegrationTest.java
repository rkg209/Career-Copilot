package com.careercopilot.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.jobs.JobPosting;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.jobs.TestJobs;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTrackerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JobPostingRepository jobPostingRepository;
  @Autowired private ApplicationRepository applicationRepository;
  @Autowired private InterviewLogRepository interviewLogRepository;

  private Long jobId;

  @BeforeEach
  void setUp() {
    interviewLogRepository.deleteAll();
    applicationRepository.deleteAll();
    jobPostingRepository.deleteAll();
    JobPosting job =
        jobPostingRepository.save(TestJobs.jobPosting("Backend Engineer", "Acme", "Java required"));
    jobId = job.getId();
  }

  @Test
  void createFromJobPosting_defaultsSnapshotAndListsBack() {
    String token = registerAndLogin("tracker-create@example.com");

    Map<String, Object> body = new HashMap<>();
    body.put("jobPostingId", jobId);
    ResponseEntity<JsonNode> created = post("/api/v1/tracker/applications", body, token);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().get("jobTitleSnapshot").asText()).isEqualTo("Backend Engineer");
    assertThat(created.getBody().get("companySnapshot").asText()).isEqualTo("Acme");
    assertThat(created.getBody().get("status").asText()).isEqualTo("SAVED");

    ResponseEntity<JsonNode> list =
        get("/api/v1/tracker/applications?page=0&size=20&sort=updatedAt,desc", token);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody().get("content")).hasSize(1);
    assertThat(list.getBody().get("page").get("totalElements").asInt()).isEqualTo(1);
  }

  @Test
  void update_validTransition_advancesStatusAndSetsAppliedAt() {
    String token = registerAndLogin("tracker-update@example.com");
    Long id = createApplication(token, jobId);

    ResponseEntity<JsonNode> updated =
        put("/api/v1/tracker/applications/" + id, Map.of("status", "APPLIED"), token);

    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody().get("status").asText()).isEqualTo("APPLIED");
    assertThat(updated.getBody().get("appliedAt").isNull()).isFalse();
  }

  @Test
  void update_illegalTransition_returns400() {
    String token = registerAndLogin("tracker-badtransition@example.com");
    Long id = createApplication(token, jobId);

    ResponseEntity<JsonNode> updated =
        put("/api/v1/tracker/applications/" + id, Map.of("status", "OFFER"), token);

    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void createManual_withoutJobPosting_requiresSnapshots() {
    String token = registerAndLogin("tracker-manual@example.com");

    ResponseEntity<JsonNode> missingSnapshot =
        post("/api/v1/tracker/applications", Map.of(), token);
    assertThat(missingSnapshot.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<JsonNode> created =
        post(
            "/api/v1/tracker/applications",
            Map.of("jobTitleSnapshot", "Freelance Gig", "companySnapshot", "Self"),
            token);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().get("jobPostingId").isNull()).isTrue();
  }

  @Test
  void interviewLogs_addAndListInOrder() {
    String token = registerAndLogin("tracker-logs@example.com");
    Long id = createApplication(token, jobId);

    post(
        "/api/v1/tracker/applications/" + id + "/interview-logs",
        Map.of("roundType", "PHONE_SCREEN", "roundNumber", 1),
        token);
    post(
        "/api/v1/tracker/applications/" + id + "/interview-logs",
        Map.of("roundType", "TECHNICAL", "roundNumber", 2, "difficulty", 4),
        token);

    ResponseEntity<JsonNode> list =
        get("/api/v1/tracker/applications/" + id + "/interview-logs", token);

    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody()).hasSize(2);
    assertThat(list.getBody().get(0).get("roundNumber").asInt()).isEqualTo(1);
    assertThat(list.getBody().get(1).get("roundNumber").asInt()).isEqualTo(2);
  }

  @Test
  void interviewLog_invalidDifficulty_returns400() {
    String token = registerAndLogin("tracker-invalidlog@example.com");
    Long id = createApplication(token, jobId);

    ResponseEntity<JsonNode> response =
        post(
            "/api/v1/tracker/applications/" + id + "/interview-logs",
            Map.of("roundType", "TECHNICAL", "difficulty", 9),
            token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void contribute_firstCallAccepted_secondCallConflicts() {
    String token = registerAndLogin("tracker-contribute@example.com");
    Long id = createApplication(token, jobId);
    ResponseEntity<JsonNode> logResponse =
        post(
            "/api/v1/tracker/applications/" + id + "/interview-logs",
            Map.of("roundType", "TECHNICAL"),
            token);
    Long logId = logResponse.getBody().get("id").asLong();

    ResponseEntity<JsonNode> first =
        post(
            "/api/v1/tracker/applications/" + id + "/interview-logs/" + logId + "/contribute",
            Map.of(),
            token);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(first.getBody().get("status").asText()).isEqualTo("PENDING");

    ResponseEntity<JsonNode> second =
        post(
            "/api/v1/tracker/applications/" + id + "/interview-logs/" + logId + "/contribute",
            Map.of(),
            token);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  private Long createApplication(String token, Long jobPostingId) {
    ResponseEntity<JsonNode> created =
        post("/api/v1/tracker/applications", Map.of("jobPostingId", jobPostingId), token);
    return created.getBody().get("id").asLong();
  }

  private String registerAndLogin(String email) {
    restTemplate.postForEntity(
        "/api/v1/auth/register", new RegisterRequestDto(email, "password123"), Object.class);
    ResponseEntity<AuthResponseDto> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login", new LoginRequestDto(email, "password123"), AuthResponseDto.class);
    return login.getBody().token();
  }

  private ResponseEntity<JsonNode> post(String path, Map<String, Object> body, String token) {
    return restTemplate.exchange(
        path, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), JsonNode.class);
  }

  private ResponseEntity<JsonNode> put(String path, Map<String, Object> body, String token) {
    return restTemplate.exchange(
        path, HttpMethod.PUT, new HttpEntity<>(body, bearer(token)), JsonNode.class);
  }

  private ResponseEntity<JsonNode> get(String path, String token) {
    return restTemplate.exchange(
        path, HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
