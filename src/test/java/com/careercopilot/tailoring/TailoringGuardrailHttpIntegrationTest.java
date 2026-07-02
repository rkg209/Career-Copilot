package com.careercopilot.tailoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.profile.ExperienceRepository;
import com.careercopilot.shared.EmbeddingGateway;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.LlmOutputException;
import com.careercopilot.shared.LlmTask;
import com.careercopilot.shared.LlmUnavailableException;
import com.careercopilot.shared.guardrail.GroundingException;
import com.careercopilot.tailoring.render.LatexRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** Verifies the HTTP status mapping of guardrail/provider failures on the tailor endpoint. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TailoringGuardrailHttpIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private ExperienceRepository experienceRepository;
  @Autowired private JobPostingRepository jobPostingRepository;

  @MockitoBean private LlmGateway llmGateway;
  @MockitoBean private EmbeddingGateway embeddingGateway;
  @MockitoBean private LatexRenderer latexRenderer;

  private Long jobId;
  private String token;

  @BeforeEach
  void setUp() {
    experienceRepository.deleteAll();
    jobPostingRepository.deleteAll();
    jobId = jobPostingRepository.save(TailoringTestFixtures.jobPosting()).getId();
    token = registerAndLogin("guardrail-user@example.com");
    UUID userId =
        userRepository.findByEmail("guardrail-user@example.com").map(User::getId).orElseThrow();
    experienceRepository.save(TailoringTestFixtures.experienceEntry(userId));
    when(embeddingGateway.embed(any(), any(), anyString()))
        .thenReturn(TailoringTestFixtures.unitVector(0));
  }

  @Test
  void groundingFailure_maps422() {
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenThrow(new GroundingException(List.of("Unknown sourceEntryId 999")));
    assertThat(tailor().getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void schemaFailureAfterRetry_maps502_withoutLeakingInternals() {
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenThrow(new LlmOutputException(LlmTask.TAILORING, List.of("$.experience: missing")));
    ResponseEntity<JsonNode> response = tailor();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody().get("message").asText()).doesNotContain("$.experience");
  }

  @Test
  void providerUnavailable_maps503() {
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenThrow(new LlmUnavailableException("connection refused", null));
    assertThat(tailor().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  private ResponseEntity<JsonNode> tailor() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return restTemplate.exchange(
        "/api/v1/tailoring/tailor",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("jobPostingId", jobId, "templateId", "classic-ats"), headers),
        JsonNode.class);
  }

  private String registerAndLogin(String email) {
    restTemplate.postForEntity(
        "/api/v1/auth/register", new RegisterRequestDto(email, "password123"), Object.class);
    ResponseEntity<AuthResponseDto> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login", new LoginRequestDto(email, "password123"), AuthResponseDto.class);
    return login.getBody().token();
  }
}
