package com.careercopilot.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.shared.EmbeddingGateway;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExperienceIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;

  @MockitoBean private EmbeddingGateway embeddingGateway;

  @BeforeEach
  void stubEmbedding() {
    when(embeddingGateway.embed(any(), any(), anyString())).thenReturn(new float[768]);
  }

  @Test
  void addExperience_returns201Pending() {
    String token = registerAndLogin("add-exp@example.com");

    ResponseEntity<JsonNode> response = post(validBody(), token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("embeddingStatus").asText()).isEqualTo("PENDING");
    assertThat(response.getBody().get("role").asText()).isEqualTo("Backend Engineer");
  }

  @Test
  void addExperience_becomesEmbeddedAsync() throws InterruptedException {
    String token = registerAndLogin("async-embed@example.com");
    long id = post(validBody(), token).getBody().get("id").asLong();

    String status = "PENDING";
    for (int i = 0; i < 50 && !"EMBEDDED".equals(status); i++) {
      Thread.sleep(100);
      JsonNode entries = get("/api/v1/profile/experience", token).getBody();
      for (JsonNode entry : entries) {
        if (entry.get("id").asLong() == id) {
          status = entry.get("embeddingStatus").asText();
        }
      }
    }
    assertThat(status).isEqualTo("EMBEDDED");
  }

  @Test
  void listExperience_returnsOnlyOwnEntriesNewestFirst() {
    String tokenA = registerAndLogin("list-a@example.com");
    String tokenB = registerAndLogin("list-b@example.com");
    Map<String, Object> older = validBody();
    older.put("startDate", "2020-01-01");
    older.put("role", "Junior Engineer");
    post(older, tokenA);
    post(validBody(), tokenA);
    post(validBody(), tokenB);

    JsonNode listA = get("/api/v1/profile/experience", tokenA).getBody();

    assertThat(listA).hasSize(2);
    assertThat(listA.get(0).get("role").asText()).isEqualTo("Backend Engineer");
    assertThat(listA.get(1).get("role").asText()).isEqualTo("Junior Engineer");
  }

  @Test
  void updateExperience_ownEntry_returns200() {
    String token = registerAndLogin("update-exp@example.com");
    long id = post(validBody(), token).getBody().get("id").asLong();
    Map<String, Object> updated = validBody();
    updated.put("role", "Staff Engineer");

    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            "/api/v1/profile/experience/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(updated, bearer(token)),
            JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("role").asText()).isEqualTo("Staff Engineer");
    assertThat(response.getBody().get("embeddingStatus").asText()).isEqualTo("PENDING");
  }

  @Test
  void updateExperience_othersEntry_returns403() {
    String tokenA = registerAndLogin("owner-403@example.com");
    String tokenB = registerAndLogin("intruder-403@example.com");
    long id = post(validBody(), tokenA).getBody().get("id").asLong();

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/profile/experience/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(validBody(), bearer(tokenB)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void deleteExperience_missing_returns404() {
    String token = registerAndLogin("delete-404@example.com");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/profile/experience/999999",
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(token)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void deleteExperience_ownEntry_returns204() {
    String token = registerAndLogin("delete-204@example.com");
    long id = post(validBody(), token).getBody().get("id").asLong();

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/profile/experience/" + id,
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(token)),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(get("/api/v1/profile/experience", token).getBody()).isEmpty();
  }

  @Test
  void addExperience_currentWithEndDate_returns400() {
    String token = registerAndLogin("bad-dates@example.com");
    Map<String, Object> body = validBody();
    body.put("isCurrent", true);
    body.put("endDate", "2024-01-01");

    assertThat(post(body, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void addExperience_missingRole_returns400() {
    String token = registerAndLogin("missing-role@example.com");
    Map<String, Object> body = validBody();
    body.remove("role");

    assertThat(post(body, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private static Map<String, Object> validBody() {
    Map<String, Object> body = new HashMap<>();
    body.put("role", "Backend Engineer");
    body.put("company", "Acme");
    body.put("location", "Bengaluru");
    body.put("startDate", "2023-01-01");
    body.put("isCurrent", false);
    body.put("endDate", "2024-06-01");
    body.put("description", "Built REST APIs on Spring Boot");
    body.put("achievements", new String[] {"Cut p99 latency 40%"});
    body.put("skillsUsed", new String[] {"Java", "Postgres"});
    return body;
  }

  private ResponseEntity<JsonNode> post(Map<String, Object> body, String token) {
    return restTemplate.exchange(
        "/api/v1/profile/experience",
        HttpMethod.POST,
        new HttpEntity<>(body, bearer(token)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> get(String path, String token) {
    return restTemplate.exchange(
        path, HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
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
