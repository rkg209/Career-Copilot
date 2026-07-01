package com.careercopilot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.auth.dto.UserResponseDto;
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
class SecurityIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void me_withoutToken_returns401() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/auth/me", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void me_withValidToken_returns200() {
    RegisterRequestDto registerDto = new RegisterRequestDto("secure@example.com", "password123");
    restTemplate.postForEntity("/api/v1/auth/register", registerDto, UserResponseDto.class);

    LoginRequestDto loginDto = new LoginRequestDto("secure@example.com", "password123");
    ResponseEntity<AuthResponseDto> loginResponse =
        restTemplate.postForEntity("/api/v1/auth/login", loginDto, AuthResponseDto.class);
    String token = loginResponse.getBody().token();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    ResponseEntity<UserResponseDto> response =
        restTemplate.exchange(
            "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), UserResponseDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void health_isPublic_returns200WithoutToken() {
    ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
