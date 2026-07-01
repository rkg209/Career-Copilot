package com.careercopilot.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.shared.EmbeddingGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
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
class ProfileIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;

  @MockitoBean private EmbeddingGateway embeddingGateway;

  @Test
  void getProfile_beforeAnyPut_returnsEmptyShell() {
    String token = registerAndLogin("empty-profile@example.com");

    ResponseEntity<JsonNode> response = get("/api/v1/profile", token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("headline").isNull()).isTrue();
    assertThat(response.getBody().get("targetRoles").isEmpty()).isTrue();
    assertThat(response.getBody().get("openToRemote").asBoolean()).isTrue();
  }

  @Test
  void putProfile_createsAndReturnsProfile() {
    String token = registerAndLogin("upsert-profile@example.com");
    Map<String, Object> body =
        Map.of(
            "headline",
            "Backend Engineer",
            "summary",
            "5 years building APIs",
            "targetRoles",
            new String[] {"SDE-2"},
            "openToRemote",
            false);

    ResponseEntity<JsonNode> put =
        restTemplate.exchange(
            "/api/v1/profile",
            HttpMethod.PUT,
            new HttpEntity<>(body, bearer(token)),
            JsonNode.class);

    assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(put.getBody().get("headline").asText()).isEqualTo("Backend Engineer");

    ResponseEntity<JsonNode> get = get("/api/v1/profile", token);
    assertThat(get.getBody().get("headline").asText()).isEqualTo("Backend Engineer");
    assertThat(get.getBody().get("openToRemote").asBoolean()).isFalse();
  }

  @Test
  void profiles_areIsolatedPerUser() {
    String tokenA = registerAndLogin("user-a-profile@example.com");
    String tokenB = registerAndLogin("user-b-profile@example.com");

    restTemplate.exchange(
        "/api/v1/profile",
        HttpMethod.PUT,
        new HttpEntity<>(Map.of("headline", "User A headline"), bearer(tokenA)),
        JsonNode.class);

    ResponseEntity<JsonNode> profileB = get("/api/v1/profile", tokenB);
    assertThat(profileB.getBody().get("headline").isNull()).isTrue();
  }

  @Test
  void profile_anonymous_returns401() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/profile", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
