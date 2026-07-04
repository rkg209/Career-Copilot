package com.careercopilot.intel;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.tracker.ApplicationRepository;
import com.careercopilot.tracker.InterviewLogRepository;
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

/**
 * End-to-end contribute flow: stages below threshold, publishes at N=3 (default config), and flips
 * the source logs' contribution status alongside the shared intel rows.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContributionIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ApplicationRepository applicationRepository;
  @Autowired private InterviewLogRepository interviewLogRepository;
  @Autowired private CompanyIntelRepository companyIntelRepository;

  @BeforeEach
  void setUp() {
    companyIntelRepository.deleteAll();
    interviewLogRepository.deleteAll();
    applicationRepository.deleteAll();
  }

  @Test
  void contribute_thirdLogForSameCompany_publishesStagedBatch() {
    String token = registerAndLogin("intel-contribute@example.com");
    String company = "Contribution Corp " + System.nanoTime();

    ContributeResult first = contributeNewLog(token, company);
    ContributeResult second = contributeNewLog(token, company);
    ContributeResult third = contributeNewLog(token, company);

    assertThat(first.status).isEqualTo("STAGED");
    assertThat(second.status).isEqualTo("STAGED");
    assertThat(third.status).isEqualTo("PUBLISHED");

    ResponseEntity<JsonNode> detail =
        restTemplate.getForEntity("/api/v1/intel/companies/" + company, JsonNode.class);
    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(detail.getBody().get("rounds").get(0).get("entryCount").asInt()).isEqualTo(3);
  }

  @Test
  void contribute_sameLogTwice_returns409() {
    String token = registerAndLogin("intel-double@example.com");
    Long appId = createApplication(token, "Double Contribute Co");
    Long logId = addInterviewLog(token, appId);

    post(
        "/api/v1/tracker/applications/" + appId + "/interview-logs/" + logId + "/contribute",
        token);
    ResponseEntity<JsonNode> second =
        post(
            "/api/v1/tracker/applications/" + appId + "/interview-logs/" + logId + "/contribute",
            token);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  private ContributeResult contributeNewLog(String token, String company) {
    Long appId = createApplication(token, company);
    Long logId = addInterviewLog(token, appId);
    ResponseEntity<JsonNode> response =
        post(
            "/api/v1/tracker/applications/" + appId + "/interview-logs/" + logId + "/contribute",
            token);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return new ContributeResult(response.getBody().get("status").asText());
  }

  private Long createApplication(String token, String company) {
    ResponseEntity<JsonNode> created =
        post2(
            "/api/v1/tracker/applications",
            Map.of("jobTitleSnapshot", "Software Engineer", "companySnapshot", company),
            token);
    return created.getBody().get("id").asLong();
  }

  private Long addInterviewLog(String token, Long applicationId) {
    ResponseEntity<JsonNode> created =
        post2(
            "/api/v1/tracker/applications/" + applicationId + "/interview-logs",
            Map.of(
                "roundType",
                "TECHNICAL",
                "questionsAsked",
                java.util.List.of("Reverse a linked list")),
            token);
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

  private ResponseEntity<JsonNode> post(String path, String token) {
    return restTemplate.exchange(
        path, HttpMethod.POST, new HttpEntity<>(Map.of(), bearer(token)), JsonNode.class);
  }

  private ResponseEntity<JsonNode> post2(String path, Map<String, Object> body, String token) {
    return restTemplate.exchange(
        path, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), JsonNode.class);
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private record ContributeResult(String status) {}
}
