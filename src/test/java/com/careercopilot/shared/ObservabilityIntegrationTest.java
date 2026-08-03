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
class ObservabilityIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void errorResponse_carriesNonBlankTraceId_matchingResponseHeader() {
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        restTemplate.getForEntity("/api/v1/auth/me", GlobalExceptionHandler.ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    String headerTraceId = response.getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
    assertThat(headerTraceId).isNotBlank();
  }

  @Test
  void inboundRequestId_isEchoedBackUnchanged() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(TraceIdFilter.TRACE_ID_HEADER, "abc123");
    ResponseEntity<String> response =
        restTemplate.exchange(
            "/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("abc123");
  }

  @Test
  void health_isPublic() {
    ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void prometheus_isUnauthenticated401_thenAuthenticated200WithHttpServerRequestsMetric() {
    ResponseEntity<String> unauth = restTemplate.getForEntity("/actuator/prometheus", String.class);
    assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    RegisterRequestDto registerDto =
        new RegisterRequestDto("observability@example.com", "password123");
    restTemplate.postForEntity("/api/v1/auth/register", registerDto, UserResponseDto.class);
    LoginRequestDto loginDto = new LoginRequestDto("observability@example.com", "password123");
    ResponseEntity<AuthResponseDto> loginResponse =
        restTemplate.postForEntity("/api/v1/auth/login", loginDto, AuthResponseDto.class);
    String token = loginResponse.getBody().token();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    ResponseEntity<String> authed =
        restTemplate.exchange(
            "/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(authed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(authed.getBody()).contains("http_server_requests_seconds");
  }
}
