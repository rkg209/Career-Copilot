package com.careercopilot.tailoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
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
import com.careercopilot.tailoring.render.LatexRenderer;
import com.careercopilot.tailoring.render.RenderFailedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TailoringPdfIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private ExperienceRepository experienceRepository;
  @Autowired private JobPostingRepository jobPostingRepository;
  @Autowired private ResumeVersionRepository resumeVersionRepository;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private LlmGateway llmGateway;
  @MockitoBean private EmbeddingGateway embeddingGateway;
  @MockitoBean private LatexRenderer latexRenderer;

  private Long jobId;
  private String token;

  @BeforeEach
  void setUp() throws Exception {
    resumeVersionRepository.deleteAll();
    experienceRepository.deleteAll();
    jobPostingRepository.deleteAll();
    jobId = jobPostingRepository.save(TailoringTestFixtures.jobPosting()).getId();
    token = registerAndLogin("pdf-user@example.com");
    UUID userId = userRepository.findByEmail("pdf-user@example.com").map(User::getId).orElseThrow();
    experienceRepository.save(TailoringTestFixtures.experienceEntry(userId));
    when(embeddingGateway.embed(any(), any(), anyString()))
        .thenReturn(TailoringTestFixtures.unitVector(0));
    JsonNode payload =
        objectMapper.readTree(
            """
            {"candidateName":"x",
             "experience":[{"sourceEntryId":1,"title":"Engineer","company":"Acme","bullets":["b"]}],
             "skills":["Java"]}""");
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenReturn(new LlmGateway.LlmJsonResult(payload, "test-model", 100));
  }

  @Test
  void getPdf_returnsApplicationPdfWithContentDisposition() {
    when(latexRenderer.render(any(), anyString())).thenReturn("%PDF-fake".getBytes());
    Long id = tailor();

    ResponseEntity<byte[]> response = getPdf(id);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .contains("resume-" + id + ".pdf");
    assertThat(new String(response.getBody())).startsWith("%PDF-");
  }

  @Test
  void tailor_renderFailure_persistsJson_thenPdfEndpointReRenders() {
    // First render fails: the tailor call surfaces 500 but the JSON row is committed.
    when(latexRenderer.render(any(), anyString())).thenThrow(new RenderFailedException("boom"));
    ResponseEntity<JsonNode> failed =
        restTemplate.exchange(
            "/api/v1/tailoring/tailor",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("jobPostingId", jobId, "templateId", "classic-ats"), bearer()),
            JsonNode.class);
    assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

    ResumeVersion stored = resumeVersionRepository.findAll().get(0);
    assertThat(stored.isRenderFailed()).isTrue();
    assertThat(stored.getPdfBytes()).isNull();
    assertThat(stored.getJsonPayload()).contains("sourceEntryId");
    assertThat(failed.getBody().get("message").asText()).contains(stored.getId().toString());

    // Renderer recovers: the PDF endpoint re-renders from the persisted JSON.
    reset(latexRenderer);
    when(latexRenderer.render(any(), anyString())).thenReturn("%PDF-recovered".getBytes());
    ResponseEntity<byte[]> retried = getPdf(stored.getId());
    assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(new String(retried.getBody())).startsWith("%PDF-");

    ResumeVersion after = resumeVersionRepository.findById(stored.getId()).orElseThrow();
    assertThat(after.getPdfBytes()).isNotEmpty();
    assertThat(after.isRenderFailed()).isFalse();
  }

  private Long tailor() {
    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            "/api/v1/tailoring/tailor",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("jobPostingId", jobId, "templateId", "classic-ats"), bearer()),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().get("resumeVersionId").asLong();
  }

  private ResponseEntity<byte[]> getPdf(Long id) {
    return restTemplate.exchange(
        "/api/v1/tailoring/resumes/" + id + "/pdf",
        HttpMethod.GET,
        new HttpEntity<>(bearer()),
        byte[].class);
  }

  private String registerAndLogin(String email) {
    restTemplate.postForEntity(
        "/api/v1/auth/register", new RegisterRequestDto(email, "password123"), Object.class);
    ResponseEntity<AuthResponseDto> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login", new LoginRequestDto(email, "password123"), AuthResponseDto.class);
    return login.getBody().token();
  }

  private HttpHeaders bearer() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
