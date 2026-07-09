package com.careercopilot.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.shared.EmbeddingGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
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

/** CRUD + per-user isolation for the Phase 2E profile sections. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProfileSectionsIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;

  @MockitoBean private EmbeddingGateway embeddingGateway;

  @Test
  void education_crudRoundTrip() {
    String token = registerAndLogin("edu-crud@example.com");

    ResponseEntity<JsonNode> created =
        post(
            "/api/v1/profile/education",
            Map.of(
                "degree", "B.Tech.",
                "institution", "IIT Bombay",
                "specialization", "Computer Science",
                "startYear", 2022,
                "endYear", 2026,
                "scoreType", "CPI",
                "scoreValue", "9.24"),
            token);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long id = created.getBody().get("id").asLong();

    ResponseEntity<JsonNode> updated =
        put(
            "/api/v1/profile/education/" + id,
            Map.of("degree", "B.Tech. (Hons.)", "institution", "IIT Bombay"),
            token);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody().get("degree").asText()).isEqualTo("B.Tech. (Hons.)");

    ResponseEntity<JsonNode> list = get("/api/v1/profile/education", token);
    assertThat(list.getBody()).hasSize(1);

    restTemplate.exchange(
        "/api/v1/profile/education/" + id,
        HttpMethod.DELETE,
        new HttpEntity<>(bearer(token)),
        Void.class);
    assertThat(get("/api/v1/profile/education", token).getBody()).isEmpty();
  }

  @Test
  void education_scoreValueWithoutType_returns400() {
    String token = registerAndLogin("edu-400@example.com");
    ResponseEntity<JsonNode> response =
        post(
            "/api/v1/profile/education",
            Map.of("degree", "B.Tech.", "institution", "IIT Bombay", "scoreValue", "9.1"),
            token);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void education_isolatedPerUser() {
    String tokenA = registerAndLogin("edu-user-a@example.com");
    String tokenB = registerAndLogin("edu-user-b@example.com");
    ResponseEntity<JsonNode> created =
        post(
            "/api/v1/profile/education",
            Map.of("degree", "M.Tech.", "institution", "IIT Delhi"),
            tokenA);
    long id = created.getBody().get("id").asLong();

    assertThat(get("/api/v1/profile/education", tokenB).getBody()).isEmpty();

    ResponseEntity<JsonNode> foreignUpdate =
        put(
            "/api/v1/profile/education/" + id,
            Map.of("degree", "Hacked", "institution", "X"),
            tokenB);
    assertThat(foreignUpdate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void skillGroups_bulkReplace() {
    String token = registerAndLogin("skills-crud@example.com");

    ResponseEntity<JsonNode> saved =
        putList(
            "/api/v1/profile/skill-groups",
            List.of(
                Map.of("name", "Programming Languages", "skills", List.of("C++", "Python")),
                Map.of("name", "Libraries", "skills", List.of("PyTorch"))),
            token);
    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(saved.getBody()).hasSize(2);

    ResponseEntity<JsonNode> replaced =
        putList(
            "/api/v1/profile/skill-groups",
            List.of(Map.of("name", "Tools", "skills", List.of("Docker"))),
            token);
    assertThat(replaced.getBody()).hasSize(1);
    assertThat(replaced.getBody().get(0).get("name").asText()).isEqualTo("Tools");

    ResponseEntity<JsonNode> list = get("/api/v1/profile/skill-groups", token);
    assertThat(list.getBody()).hasSize(1);
  }

  @Test
  void skillGroups_duplicateNames_returns400() {
    String token = registerAndLogin("skills-400@example.com");
    ResponseEntity<JsonNode> response =
        putList(
            "/api/v1/profile/skill-groups",
            List.of(
                Map.of("name", "Languages", "skills", List.of("C")),
                Map.of("name", "languages", "skills", List.of("Go"))),
            token);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void highlights_crudAndTypeFilter() {
    String token = registerAndLogin("highlights-crud@example.com");

    post(
        "/api/v1/profile/highlights",
        Map.of("type", "SCHOLASTIC_ACHIEVEMENT", "text", "JEE 97 percentile", "yearLabel", "2022"),
        token);
    ResponseEntity<JsonNode> cert =
        post(
            "/api/v1/profile/highlights",
            Map.of("type", "CERTIFICATION", "text", "AWS SAA (Amazon)"),
            token);
    assertThat(cert.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    assertThat(get("/api/v1/profile/highlights", token).getBody()).hasSize(2);
    ResponseEntity<JsonNode> onlyCerts =
        get("/api/v1/profile/highlights?type=CERTIFICATION", token);
    assertThat(onlyCerts.getBody()).hasSize(1);
    assertThat(onlyCerts.getBody().get(0).get("text").asText()).contains("AWS");

    long certId = cert.getBody().get("id").asLong();
    restTemplate.exchange(
        "/api/v1/profile/highlights/" + certId,
        HttpMethod.DELETE,
        new HttpEntity<>(bearer(token)),
        Void.class);
    assertThat(get("/api/v1/profile/highlights?type=CERTIFICATION", token).getBody()).isEmpty();
  }

  @Test
  void profile_courseworkAndInterests_persist() {
    String token = registerAndLogin("coursework@example.com");
    ResponseEntity<JsonNode> saved =
        put(
            "/api/v1/profile",
            Map.of(
                "coursework", List.of("Operating Systems", "Databases"),
                "areasOfInterest", List.of("Distributed Systems")),
            token);
    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(saved.getBody().get("coursework")).hasSize(2);

    ResponseEntity<JsonNode> fetched = get("/api/v1/profile", token);
    assertThat(fetched.getBody().get("areasOfInterest").get(0).asText())
        .isEqualTo("Distributed Systems");
  }

  @Test
  void sections_anonymous_return401() {
    assertThat(restTemplate.getForEntity("/api/v1/profile/education", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            restTemplate.getForEntity("/api/v1/profile/skill-groups", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            restTemplate.getForEntity("/api/v1/profile/highlights", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private ResponseEntity<JsonNode> post(String path, Map<String, ?> body, String token) {
    return restTemplate.exchange(
        path, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), JsonNode.class);
  }

  private ResponseEntity<JsonNode> put(String path, Map<String, ?> body, String token) {
    return restTemplate.exchange(
        path, HttpMethod.PUT, new HttpEntity<>(body, bearer(token)), JsonNode.class);
  }

  private ResponseEntity<JsonNode> putList(String path, List<?> body, String token) {
    return restTemplate.exchange(
        path, HttpMethod.PUT, new HttpEntity<>(body, bearer(token)), JsonNode.class);
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
