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
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * No endpoint or response body under this feature may ever expose user identity or private text.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntelPrivacyIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ApplicationRepository applicationRepository;
  @Autowired private InterviewLogRepository interviewLogRepository;
  @Autowired private CompanyIntelRepository companyIntelRepository;

  private static final String EMAIL = "intel-privacy@example.com";

  @BeforeEach
  void setUp() {
    companyIntelRepository.deleteAll();
    interviewLogRepository.deleteAll();
    applicationRepository.deleteAll();
  }

  @Test
  void contributeAndBrowseResponses_neverContainUserIdentity() {
    String token = registerAndLogin(EMAIL);
    String company = "Privacy Co " + System.nanoTime();
    String userId = decodeUserIdFromLogin(token);

    ResponseEntity<JsonNode> lastContribute = null;
    for (int i = 0; i < 3; i++) {
      lastContribute = contributeNewLog(token, company, "Contact me at jane.doe@example.com");
    }
    assertNoLeak(lastContribute.getBody().toString(), userId);

    ResponseEntity<JsonNode> browse =
        restTemplate.getForEntity("/api/v1/intel/companies?page=0&size=20", JsonNode.class);
    assertNoLeak(browse.getBody().toString(), userId);

    ResponseEntity<JsonNode> detail =
        restTemplate.getForEntity("/api/v1/intel/companies/" + company, JsonNode.class);
    assertNoLeak(detail.getBody().toString(), userId);
  }

  private void assertNoLeak(String json, String userId) {
    assertThat(json)
        .doesNotContain(EMAIL)
        .doesNotContain(userId)
        .doesNotContainIgnoringCase("jane.doe@example.com")
        .doesNotContainIgnoringCase("\"userId\"")
        .doesNotContainIgnoringCase("\"applicationId\"");
  }

  private ResponseEntity<JsonNode> contributeNewLog(String token, String company, String notes) {
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
                    "roundType",
                    "TECHNICAL",
                    "questionsAsked",
                    List.of("Reverse a linked list"),
                    "notes",
                    notes),
                token)
            .getBody()
            .get("id")
            .asLong();
    return post(
        "/api/v1/tracker/applications/" + appId + "/interview-logs/" + logId + "/contribute",
        Map.of(),
        token);
  }

  private String decodeUserIdFromLogin(String token) {
    String[] parts = token.split("\\.");
    byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
    String json = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper()
          .readTree(json)
          .get("userId")
          .asText();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
