package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.jobs.dto.JobPasteRequest;
import com.fasterxml.jackson.databind.JsonNode;
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
class JobPasteIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void paste_authenticated_returns201WithManualSource() {
    String token = registerAndLogin("paster@example.com");
    JobPasteRequest req =
        new JobPasteRequest(
            "Platform Engineer", "Hooli", "Remote", "https://hooli.test/jobs/1", "x".repeat(120));

    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            "/api/v1/jobs/paste",
            HttpMethod.POST,
            new HttpEntity<>(req, bearer(token)),
            JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("source").asText()).isEqualTo("MANUAL_PASTE");
    assertThat(response.getBody().get("title").asText()).isEqualTo("Platform Engineer");
  }

  @Test
  void paste_anonymous_returns401() {
    JobPasteRequest req = new JobPasteRequest("Engineer", "Acme", null, null, "x".repeat(60));
    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/v1/jobs/paste", req, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void paste_shortText_returns400() {
    String token = registerAndLogin("shorttext@example.com");
    JobPasteRequest req = new JobPasteRequest("Engineer", "Acme", null, null, "too short");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/jobs/paste",
            HttpMethod.POST,
            new HttpEntity<>(req, bearer(token)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private String registerAndLogin(String email) {
    restTemplate.postForEntity(
        "/api/v1/auth/register", new RegisterRequestDto(email, "password123"), Object.class);
    ResponseEntity<AuthResponseDto> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login", new LoginRequestDto(email, "password123"), AuthResponseDto.class);
    return login.getBody().token();
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
