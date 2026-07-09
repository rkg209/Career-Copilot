package com.careercopilot.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.shared.EmbeddingGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Typed corpus entries persist, default correctly, and flow through vector retrieval. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TypedCorpusIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private ExperienceRepository experienceRepository;

  @MockitoBean private EmbeddingGateway embeddingGateway;

  @Test
  void projectEntry_persistsTypedFieldsThroughApi() {
    String token = registerAndLogin("typed-project@example.com");

    ResponseEntity<JsonNode> created =
        post(
            "/api/v1/profile/experience",
            Map.of(
                "role", "Quadcopter motor-failure control",
                "company", "Inter-IIT Team",
                "startDate", "2024-10-01",
                "entryType", "PROJECT",
                "projectType", "COURSE",
                "guide", "Prof. Vachhani",
                "context", "Inter-IIT Tech Meet 2024",
                "url", "https://github.com/x/quad"),
            token);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().get("entryType").asText()).isEqualTo("PROJECT");
    assertThat(created.getBody().get("projectType").asText()).isEqualTo("COURSE");
    assertThat(created.getBody().get("guide").asText()).isEqualTo("Prof. Vachhani");

    ResponseEntity<JsonNode> list = get("/api/v1/profile/experience", token);
    assertThat(list.getBody().get(0).get("context").asText()).isEqualTo("Inter-IIT Tech Meet 2024");
  }

  @Test
  void legacyEntry_withoutEntryType_defaultsToWork() {
    String token = registerAndLogin("typed-legacy@example.com");

    ResponseEntity<JsonNode> created =
        post(
            "/api/v1/profile/experience",
            Map.of("role", "Engineer", "company", "Acme", "startDate", "2022-01-01"),
            token);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().get("entryType").asText()).isEqualTo("WORK");
    assertThat(created.getBody().get("projectType").isNull()).isTrue();
  }

  @Test
  void projectTypeOnNonProject_returns400() {
    String token = registerAndLogin("typed-400@example.com");

    ResponseEntity<JsonNode> response =
        post(
            "/api/v1/profile/experience",
            Map.of(
                "role", "Engineer",
                "company", "Acme",
                "startDate", "2022-01-01",
                "entryType", "WORK",
                "projectType", "SELF"),
            token);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void typedEntries_areReturnedByVectorRetrieval() {
    String token = registerAndLogin("typed-retrieval@example.com");
    UUID userId =
        userRepository.findByEmail("typed-retrieval@example.com").map(User::getId).orElseThrow();

    ExperienceEntry project = new ExperienceEntry(userId);
    project.setRole("HVAE satellite generation");
    project.setCompany("GNR638");
    project.setStartDate(LocalDate.of(2025, 3, 1));
    project.setEntryType(CorpusEntryType.PROJECT);
    project.setProjectType(ProjectType.COURSE);
    project.setEmbedding(unitVector(0));
    project.setEmbeddedAt(Instant.now());
    experienceRepository.save(project);

    ExperienceEntry por = new ExperienceEntry(userId);
    por.setRole("Team Lead");
    por.setCompany("Mars Rover Team");
    por.setStartDate(LocalDate.of(2024, 5, 1));
    por.setEntryType(CorpusEntryType.POSITION_OF_RESPONSIBILITY);
    por.setEmbedding(unitVector(1));
    por.setEmbeddedAt(Instant.now());
    experienceRepository.save(por);

    List<ExperienceEntry> retrieved =
        experienceRepository.findTopKByVector(userId, unitVector(0), Limit.of(5));

    assertThat(retrieved).hasSize(2);
    assertThat(retrieved.get(0).getEntryType()).isEqualTo(CorpusEntryType.PROJECT);
    assertThat(retrieved)
        .extracting(ExperienceEntry::getEntryType)
        .contains(CorpusEntryType.POSITION_OF_RESPONSIBILITY);
    assertThat(token).isNotBlank();
  }

  private static float[] unitVector(int axis) {
    float[] vector = new float[768];
    vector[axis] = 1f;
    return vector;
  }

  private ResponseEntity<JsonNode> post(String path, Map<String, ?> body, String token) {
    return restTemplate.exchange(
        path, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), JsonNode.class);
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
