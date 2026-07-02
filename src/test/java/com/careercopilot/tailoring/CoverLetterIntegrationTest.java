package com.careercopilot.tailoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.careercopilot.shared.LlmTask;
import com.careercopilot.tailoring.render.LatexRenderer;
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
class CoverLetterIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private ExperienceRepository experienceRepository;
  @Autowired private JobPostingRepository jobPostingRepository;
  @Autowired private CoverLetterRepository coverLetterRepository;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private LlmGateway llmGateway;
  @MockitoBean private EmbeddingGateway embeddingGateway;
  @MockitoBean private LatexRenderer latexRenderer;

  private Long jobId;
  private String token;

  @BeforeEach
  void setUp() throws Exception {
    coverLetterRepository.deleteAll();
    experienceRepository.deleteAll();
    jobPostingRepository.deleteAll();
    jobId = jobPostingRepository.save(TailoringTestFixtures.jobPosting()).getId();
    token = registerAndLogin("cover-user@example.com");
    UUID userId =
        userRepository.findByEmail("cover-user@example.com").map(User::getId).orElseThrow();
    experienceRepository.save(TailoringTestFixtures.experienceEntry(userId));

    when(embeddingGateway.embed(any(), any(), anyString()))
        .thenReturn(TailoringTestFixtures.unitVector(0));
    when(latexRenderer.render(any(), anyString())).thenReturn("%PDF-cover".getBytes());
    JsonNode payload =
        objectMapper.readTree(
            """
            {"candidateName":"x","salutation":"Dear Hiring Manager,",
             "bodyParagraphs":["I built backend services at Acme."],"closing":"Sincerely,"}""");
    when(llmGateway.callForJson(
            any(), eq(LlmTask.COVER_LETTER), anyString(), anyString(), anyString(), any()))
        .thenReturn(new LlmGateway.LlmJsonResult(payload, "test-model", 500));
  }

  @Test
  void coverLetter_onDemand_persistedAndDownloadable() {
    ResponseEntity<JsonNode> created =
        restTemplate.exchange(
            "/api/v1/tailoring/cover-letter",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("jobPostingId", jobId), bearer()),
            JsonNode.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().get("templateId").asText()).isEqualTo("cover-standard");
    assertThat(created.getBody().get("renderStatus").asText()).isEqualTo("RENDERED");

    Long id = created.getBody().get("coverLetterId").asLong();
    CoverLetter stored = coverLetterRepository.findById(id).orElseThrow();
    assertThat(stored.getJsonPayload()).contains("bodyParagraphs");
    assertThat(stored.getPdfBytes()).isNotEmpty();

    ResponseEntity<byte[]> pdf =
        restTemplate.exchange(
            "/api/v1/tailoring/cover-letters/" + id + "/pdf",
            HttpMethod.GET,
            new HttpEntity<>(bearer()),
            byte[].class);
    assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(pdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
  }

  @Test
  void coverLetter_unknownResumeVersion_returns404() {
    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            "/api/v1/tailoring/cover-letter",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("jobPostingId", jobId, "resumeVersionId", 424242), bearer()),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
