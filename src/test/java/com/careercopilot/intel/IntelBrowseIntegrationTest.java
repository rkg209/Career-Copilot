package com.careercopilot.intel;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.tracker.ApplicationRepository;
import com.careercopilot.tracker.InterviewLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
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
 * Public {@code GET /api/v1/intel/*} endpoints need no auth and only ever surface published rows.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntelBrowseIntegrationTest {

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
  void browseCompanies_noAuthRequired_hiddenBelowThreshold() {
    String token = registerAndLogin("intel-browse@example.com");
    String company = "Browse Co " + System.nanoTime();
    contributeNewLog(token, company);
    contributeNewLog(token, company);

    ResponseEntity<JsonNode> response =
        restTemplate.getForEntity("/api/v1/intel/companies?page=0&size=20", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    boolean present = false;
    for (JsonNode node : response.getBody().get("content")) {
      if (node.get("company").asText().equals(company)) {
        present = true;
      }
    }
    assertThat(present).isFalse();
  }

  @Test
  void byCompany_publishedAfterThreshold_visibleWithoutAuth() {
    String token = registerAndLogin("intel-browse2@example.com");
    String company = "Visible Co " + System.nanoTime();
    contributeNewLog(token, company);
    contributeNewLog(token, company);
    contributeNewLog(token, company);

    ResponseEntity<JsonNode> detail =
        restTemplate.getForEntity("/api/v1/intel/companies/" + company, JsonNode.class);

    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(detail.getBody().get("company").asText()).isEqualTo(company);
    assertThat(detail.getBody().get("rounds").get(0).get("roundType").asText())
        .isEqualTo("TECHNICAL");
  }

  @Test
  void byCompany_unknownCompany_returns404() {
    ResponseEntity<JsonNode> response =
        restTemplate.getForEntity("/api/v1/intel/companies/Nonexistent-Co", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private void contributeNewLog(String token, String company) {
    Long appId =
        post(
                "/api/v1/tracker/applications",
                Map.of("jobTitleSnapshot", "Software Engineer", "companySnapshot", company),
                token)
            .getBody()
            .get("id")
            .asLong();
    Long logId =
        post(
                "/api/v1/tracker/applications/" + appId + "/interview-logs",
                Map.of(
                    "roundType", "TECHNICAL", "questionsAsked", List.of("Reverse a linked list")),
                token)
            .getBody()
            .get("id")
            .asLong();
    post(
        "/api/v1/tracker/applications/" + appId + "/interview-logs/" + logId + "/contribute",
        Map.of(),
        token);
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

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
