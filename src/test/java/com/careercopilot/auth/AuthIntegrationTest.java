package com.careercopilot.auth;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void registerThenLoginThenMe_fullFlowSucceeds() {
    RegisterRequestDto registerDto = new RegisterRequestDto("flow@example.com", "password123");
    ResponseEntity<UserResponseDto> registerResponse =
        restTemplate.postForEntity("/api/v1/auth/register", registerDto, UserResponseDto.class);
    assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(registerResponse.getBody().email()).isEqualTo("flow@example.com");

    LoginRequestDto loginDto = new LoginRequestDto("flow@example.com", "password123");
    ResponseEntity<AuthResponseDto> loginResponse =
        restTemplate.postForEntity("/api/v1/auth/login", loginDto, AuthResponseDto.class);
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    String token = loginResponse.getBody().token();
    assertThat(token).isNotBlank();

    var headers = new org.springframework.http.HttpHeaders();
    headers.setBearerAuth(token);
    ResponseEntity<UserResponseDto> meResponse =
        restTemplate.exchange(
            "/api/v1/auth/me",
            org.springframework.http.HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(headers),
            UserResponseDto.class);
    assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(meResponse.getBody().email()).isEqualTo("flow@example.com");
  }

  @Test
  void register_duplicateEmail_returns409() {
    RegisterRequestDto dto = new RegisterRequestDto("dupe@example.com", "password123");
    restTemplate.postForEntity("/api/v1/auth/register", dto, UserResponseDto.class);

    ResponseEntity<String> secondResponse =
        restTemplate.postForEntity("/api/v1/auth/register", dto, String.class);

    assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void login_wrongPassword_returns401() {
    RegisterRequestDto registerDto = new RegisterRequestDto("badlogin@example.com", "password123");
    restTemplate.postForEntity("/api/v1/auth/register", registerDto, UserResponseDto.class);

    LoginRequestDto loginDto = new LoginRequestDto("badlogin@example.com", "wrong-password");
    ResponseEntity<String> loginResponse =
        restTemplate.postForEntity("/api/v1/auth/login", loginDto, String.class);

    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
