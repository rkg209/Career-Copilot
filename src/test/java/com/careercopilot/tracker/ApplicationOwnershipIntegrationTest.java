package com.careercopilot.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.jobs.JobPosting;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.jobs.TestJobs;
import com.fasterxml.jackson.databind.JsonNode;
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

/** Per-user isolation (CLAUDE.md privacy rules) + snapshot survives job-posting deletion. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationOwnershipIntegrationTest {

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
        jobPostingRepository.save(TestJobs.jobPosting("Engineer", "Acme", "Java required"));
    jobId = job.getId();
  }

  @Test
  void update_otherUsersApplication_returns403() {
    String ownerToken = registerAndLogin("owner@example.com");
    Long id = createApplication(ownerToken, jobId);
    String intruderToken = registerAndLogin("intruder@example.com");

    ResponseEntity<JsonNode> response =
        put("/api/v1/tracker/applications/" + id, Map.of("notes", "snooping"), intruderToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void interviewLogs_otherUsersApplication_returns403() {
    String ownerToken = registerAndLogin("owner2@example.com");
    Long id = createApplication(ownerToken, jobId);
    String intruderToken = registerAndLogin("intruder2@example.com");

    ResponseEntity<JsonNode> response =
        get("/api/v1/tracker/applications/" + id + "/interview-logs", intruderToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void update_unknownApplication_returns404() {
    String token = registerAndLogin("owner3@example.com");

    ResponseEntity<JsonNode> response =
        put("/api/v1/tracker/applications/999999", Map.of("notes", "x"), token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void snapshot_survivesJobPostingDeletion() {
    String token = registerAndLogin("owner4@example.com");
    Long id = createApplication(token, jobId);

    jobPostingRepository.deleteById(jobId);

    Application application = applicationRepository.findById(id).orElseThrow();
    assertThat(application.getJobPostingId()).isNull();
    assertThat(application.getJobTitleSnapshot()).isEqualTo("Engineer");
    assertThat(application.getCompanySnapshot()).isEqualTo("Acme");
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
