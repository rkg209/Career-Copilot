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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TailoringOwnershipIntegrationTest {

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

  private String tokenA;
  private String tokenB;
  private Long resumeIdOfA;

  @BeforeEach
  void setUp() throws Exception {
    resumeVersionRepository.deleteAll();
    experienceRepository.deleteAll();
    jobPostingRepository.deleteAll();
    Long jobId = jobPostingRepository.save(TailoringTestFixtures.jobPosting()).getId();

    when(embeddingGateway.embed(any(), any(), anyString()))
        .thenReturn(TailoringTestFixtures.unitVector(0));
    when(latexRenderer.render(any(), anyString())).thenReturn("%PDF-fake".getBytes());
    JsonNode payload =
        objectMapper.readTree(
            """
            {"candidateName":"x",
             "experience":[{"sourceEntryId":1,"title":"Engineer","company":"Acme","bullets":["b"]}],
             "skills":["Java"]}""");
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenReturn(new LlmGateway.LlmJsonResult(payload, "test-model", 100));

    tokenA = registerAndLogin("owner-a@example.com");
    tokenB = registerAndLogin("owner-b@example.com");
    UUID userA = userRepository.findByEmail("owner-a@example.com").map(User::getId).orElseThrow();
    experienceRepository.save(TailoringTestFixtures.experienceEntry(userA));

    ResponseEntity<JsonNode> created =
        restTemplate.exchange(
            "/api/v1/tailoring/tailor",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("jobPostingId", jobId, "templateId", "classic-ats"), bearer(tokenA)),
            JsonNode.class);
    resumeIdOfA = created.getBody().get("resumeVersionId").asLong();
  }

  @Test
  void get_othersResume_returns403() {
    assertThat(get("/api/v1/tailoring/resumes/" + resumeIdOfA, tokenB).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(get("/api/v1/tailoring/resumes/" + resumeIdOfA + "/pdf", tokenB).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void get_missingResume_returns404() {
    assertThat(get("/api/v1/tailoring/resumes/999999", tokenA).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void list_neverLeaksOtherUsersResumes() {
    ResponseEntity<JsonNode> listB = get("/api/v1/tailoring/resumes", tokenB);
    assertThat(listB.getBody().get("content")).isEmpty();

    ResponseEntity<JsonNode> listA = get("/api/v1/tailoring/resumes", tokenA);
    assertThat(listA.getBody().get("content")).hasSize(1);
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
