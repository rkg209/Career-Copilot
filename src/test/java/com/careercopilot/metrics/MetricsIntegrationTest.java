package com.careercopilot.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.auth.dto.UserResponseDto;
import java.util.Locale;
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
class MetricsIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;

  private String registerAndLogin(String email) {
    RegisterRequestDto registerDto = new RegisterRequestDto(email, "password123");
    restTemplate.postForEntity("/api/v1/auth/register", registerDto, UserResponseDto.class);
    LoginRequestDto loginDto = new LoginRequestDto(email, "password123");
    ResponseEntity<AuthResponseDto> loginResponse =
        restTemplate.postForEntity("/api/v1/auth/login", loginDto, AuthResponseDto.class);
    return loginResponse.getBody().token();
  }

  @Test
  void atsImprovement_requiresAuth_returns401Unauthenticated() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/metrics/ats-improvement", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void atsImprovement_authenticated_returnsAggregateWithNoUserIdentity() {
    String token = registerAndLogin("metrics-ats@example.com");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/metrics/ats-improvement",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertNoUserIdentity(response.getBody());
  }

  @Test
  void tokens_requiresAuth_returns401Unauthenticated() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/metrics/tokens", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tokens_authenticated_returns200WithNoUserIdentity() {
    String token = registerAndLogin("metrics-tokens@example.com");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/metrics/tokens", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertNoUserIdentity(response.getBody());
  }

  private void assertNoUserIdentity(String body) {
    String lower = body.toLowerCase(Locale.ROOT);
    assertThat(lower).doesNotContain("userid");
    assertThat(lower).doesNotContain("@example.com");
  }
}
