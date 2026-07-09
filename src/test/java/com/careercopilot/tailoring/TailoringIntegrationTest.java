package com.careercopilot.tailoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.jobs.JobPosting;
import com.careercopilot.jobs.JobPostingRepository;
import com.careercopilot.profile.EducationEntry;
import com.careercopilot.profile.EducationRepository;
import com.careercopilot.profile.EducationScoreType;
import com.careercopilot.profile.ExperienceRepository;
import com.careercopilot.profile.SkillGroup;
import com.careercopilot.profile.SkillGroupRepository;
import com.careercopilot.shared.EmbeddingGateway;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.tailoring.render.LatexRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
class TailoringIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private ExperienceRepository experienceRepository;
  @Autowired private EducationRepository educationRepository;
  @Autowired private SkillGroupRepository skillGroupRepository;
  @Autowired private JobPostingRepository jobPostingRepository;
  @Autowired private ResumeVersionRepository resumeVersionRepository;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private LlmGateway llmGateway;
  @MockitoBean private EmbeddingGateway embeddingGateway;
  @MockitoBean private LatexRenderer latexRenderer;

  private Long jobId;

  @BeforeEach
  void setUp() {
    resumeVersionRepository.deleteAll();
    experienceRepository.deleteAll();
    jobPostingRepository.deleteAll();
    JobPosting job = jobPostingRepository.save(TailoringTestFixtures.jobPosting());
    jobId = job.getId();
    when(embeddingGateway.embed(any(), any(), anyString()))
        .thenReturn(TailoringTestFixtures.unitVector(0));
    when(latexRenderer.render(any(), anyString())).thenReturn("%PDF-fake".getBytes());
  }

  @Test
  void tailor_fullFlow_persistsJsonAndPdf() throws Exception {
    String token = seedUserWithCorpus("tailor-happy@example.com");
    stubLlmSuccess();

    ResponseEntity<JsonNode> response =
        post(
            "/api/v1/tailoring/tailor",
            Map.of("jobPostingId", jobId, "templateId", "classic-ats"),
            token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("renderStatus").asText()).isEqualTo("RENDERED");
    assertThat(response.getBody().get("llmModel").asText()).isEqualTo("test-model");

    Long id = response.getBody().get("resumeVersionId").asLong();
    ResumeVersion stored = resumeVersionRepository.findById(id).orElseThrow();
    assertThat(stored.getJsonPayload()).contains("sourceEntryId");
    assertThat(stored.getPdfBytes()).isNotEmpty();

    // Contact info was injected deterministically (never through the LLM).
    JsonNode payload = objectMapper.readTree(stored.getJsonPayload());
    assertThat(payload.get("contactInfo").get("email").asText())
        .isEqualTo("tailor-happy@example.com");

    ResponseEntity<JsonNode> list = get("/api/v1/tailoring/resumes", token);
    assertThat(list.getBody().get("content")).hasSize(1);

    ResponseEntity<JsonNode> detail = get("/api/v1/tailoring/resumes/" + id, token);
    assertThat(detail.getBody().get("jsonPayload").get("candidateName").asText()).isNotBlank();
  }

  @Test
  void tailor_unknownJob_returns404() {
    String token = seedUserWithCorpus("tailor-404@example.com");
    ResponseEntity<JsonNode> response =
        post(
            "/api/v1/tailoring/tailor",
            Map.of("jobPostingId", 999999, "templateId", "classic-ats"),
            token);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void tailor_unknownTemplate_returns400() {
    String token = seedUserWithCorpus("tailor-400@example.com");
    ResponseEntity<JsonNode> response =
        post(
            "/api/v1/tailoring/tailor",
            Map.of("jobPostingId", jobId, "templateId", "fancy"),
            token);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void tailor_withoutExperience_returns422() {
    String token = registerAndLogin("tailor-empty@example.com");
    ResponseEntity<JsonNode> response =
        post(
            "/api/v1/tailoring/tailor",
            Map.of("jobPostingId", jobId, "templateId", "classic-ats"),
            token);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void tailor_anonymous_returns401() {
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v1/tailoring/tailor",
            Map.of("jobPostingId", jobId, "templateId", "classic-ats"),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tailor_sanitizesAndFencesJobDescription() {
    String token = seedUserWithCorpus("tailor-inject@example.com");
    stubLlmSuccess();

    post("/api/v1/tailoring/tailor", Map.of("jobPostingId", jobId, "templateId", "compact"), token);

    ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
    verify(llmGateway)
        .callForJson(any(), any(), anyString(), userPrompt.capture(), anyString(), any());
    assertThat(userPrompt.getValue())
        .contains("--- BEGIN JOB DESCRIPTION")
        .contains("[CONTENT_REMOVED]")
        .doesNotContainIgnoringCase("ignore all instructions");
  }

  @Test
  void listTemplates_returnsCatalog() {
    String token = registerAndLogin("templates@example.com");
    ResponseEntity<JsonNode> response = get("/api/v1/tailoring/templates", token);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(5);
  }

  @Test
  void tailor_injectsProfileSectionsDeterministically() throws Exception {
    String token = seedUserWithCorpus("tailor-sections@example.com");
    UUID userId =
        userRepository.findByEmail("tailor-sections@example.com").map(User::getId).orElseThrow();

    EducationEntry education = new EducationEntry(userId);
    education.setDegree("B.Tech.");
    education.setInstitution("IIT Bombay");
    education.setScoreType(EducationScoreType.CPI);
    education.setScoreValue("9.2");
    educationRepository.save(education);

    SkillGroup group = new SkillGroup(userId);
    group.setName("Languages");
    group.setSkills(java.util.List.of("Java", "Python"));
    skillGroupRepository.save(group);

    // LLM returns projects + PORs (schema-valid) but never education/skills sections.
    JsonNode llmPayload =
        objectMapper.readTree(
            """
            {"candidateName":"x",
             "projects":[{"sourceEntryId":1,"name":"Quadcopter","bullets":["b"]}],
             "positionsOfResponsibility":[{"sourceEntryId":2,"name":"Team Lead","bullets":["c"]}],
             "skills":["Java"]}""");
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenReturn(new LlmGateway.LlmJsonResult(llmPayload.deepCopy(), "test-model", 99));

    ResponseEntity<JsonNode> response =
        post(
            "/api/v1/tailoring/tailor",
            Map.of("jobPostingId", jobId, "templateId", "classic-ats"),
            token);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    Long id = response.getBody().get("resumeVersionId").asLong();
    JsonNode payload =
        objectMapper.readTree(resumeVersionRepository.findById(id).orElseThrow().getJsonPayload());

    // Sections injected after the LLM call from structured profile facts.
    assertThat(payload.get("education").get(0).get("score").asText()).isEqualTo("CPI 9.2");
    assertThat(payload.get("skillGroups").get(0).get("skillsJoined").asText())
        .isEqualTo("Java, Python");
    // The LLM's typed arrays survive untouched.
    assertThat(payload.get("projects").get(0).get("name").asText()).isEqualTo("Quadcopter");
    assertThat(payload.get("positionsOfResponsibility").get(0).get("name").asText())
        .isEqualTo("Team Lead");
  }

  private void stubLlmSuccess() {
    try {
      JsonNode payload =
          objectMapper.readTree(
              """
              {"candidateName":"x",
               "experience":[{"sourceEntryId":1,"title":"Engineer","company":"Acme","bullets":["b"]}],
               "skills":["Java"]}""");
      when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
          .thenReturn(new LlmGateway.LlmJsonResult(payload.deepCopy(), "test-model", 1234));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String seedUserWithCorpus(String email) {
    String token = registerAndLogin(email);
    UUID userId = userRepository.findByEmail(email).map(User::getId).orElseThrow();
    experienceRepository.save(TailoringTestFixtures.experienceEntry(userId));
    return token;
  }

  private String registerAndLogin(String email) {
    restTemplate.postForEntity(
        "/api/v1/auth/register", new RegisterRequestDto(email, "password123"), Object.class);
    ResponseEntity<AuthResponseDto> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login", new LoginRequestDto(email, "password123"), AuthResponseDto.class);
    return login.getBody().token();
  }

  private ResponseEntity<JsonNode> post(String path, Map<String, Object> body, String token) {
    return restTemplate.exchange(
        path, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), JsonNode.class);
  }

  private ResponseEntity<JsonNode> get(String path, String token) {
    return restTemplate.exchange(
        path, HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
