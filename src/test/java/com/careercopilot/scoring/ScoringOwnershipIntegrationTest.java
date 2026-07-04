package com.careercopilot.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.jobs.JobPosting;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.jobs.TestJobs;
import com.careercopilot.shared.EmbeddingGateway;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.tailoring.ResumeVersion;
import com.careercopilot.tailoring.ResumeVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Per-user isolation: no scoring access across users (CLAUDE.md privacy rules). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScoringOwnershipIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private JobPostingRepository jobPostingRepository;
  @Autowired private ResumeVersionRepository resumeVersionRepository;
  @Autowired private ScoringResultRepository scoringResultRepository;

  @MockitoBean private LlmGateway llmGateway;
  @MockitoBean private EmbeddingGateway embeddingGateway;

  private Long jobId;

  @BeforeEach
  void setUp() {
    scoringResultRepository.deleteAll();
    resumeVersionRepository.deleteAll();
    jobPostingRepository.deleteAll();
    JobPosting job =
        jobPostingRepository.save(TestJobs.jobPosting("Engineer", "Acme", "Java required"));
    jobId = job.getId();
  }

  @Test
  void score_otherUsersResume_returns403() {
    String ownerToken = registerAndLogin("owner@example.com");
    Long resumeId = seedResume("owner@example.com");
    String intruderToken = registerAndLogin("intruder@example.com");

    ResponseEntity<JsonNode> response =
        post(
            "/api/v1/scoring/score",
            Map.of("resumeVersionId", resumeId, "jobPostingId", jobId),
            intruderToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(scoringResultRepository.count()).isZero();
    assertThat(ownerToken).isNotEqualTo(intruderToken);
  }

  @Test
  void results_otherUsersResume_returns403() {
    registerAndLogin("owner2@example.com");
    Long resumeId = seedResume("owner2@example.com");
    String intruderToken = registerAndLogin("intruder2@example.com");

    ResponseEntity<JsonNode> response = get("/api/v1/scoring/results/" + resumeId, intruderToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void results_noScoreYet_returns404() {
    String token = registerAndLogin("owner3@example.com");
    Long resumeId = seedResume("owner3@example.com");

    ResponseEntity<JsonNode> response = get("/api/v1/scoring/results/" + resumeId, token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private Long seedResume(String email) {
    UUID userId = userRepository.findByEmail(email).map(User::getId).orElseThrow();
    ResumeVersion resume =
        resumeVersionRepository.save(
            new ResumeVersion(
                userId,
                jobId,
                "classic-ats",
                """
                {"candidateName":"x","experience":[{"sourceEntryId":1,"title":"Engineer",
                 "company":"Acme","bullets":["Java"]}],"skills":["Java"]}""",
                "test-model",
                100));
    return resume.getId();
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
