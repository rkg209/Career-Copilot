package com.careercopilot.trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.LlmOutputException;
import com.careercopilot.shared.LlmTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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

/** Generate endpoint: persistence, owner-scoping, grounding (422), and schema failure (502). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrainerGenerateIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private MockQuestionRepository mockQuestionRepository;

  @MockitoBean private LlmGateway llmGateway;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    mockQuestionRepository.deleteAll();
  }

  @Test
  void generate_persistsQuestionsAndOwnerScopesTheListing() throws Exception {
    stubLlm(
        """
        {"questions":[
          {"questionText":"Tell me about a challenging project.","source":"ROLE_PATTERN","sourceRefId":null},
          {"questionText":"How do you approach debugging?","source":"ROLE_PATTERN","sourceRefId":null}
        ]}""");

    String tokenA = registerAndLogin("trainer-gen-a@example.com");
    ResponseEntity<JsonNode> response =
        generate(tokenA, Map.of("roleCategory", "Software Engineer", "count", 2));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).hasSize(2);

    String tokenB = registerAndLogin("trainer-gen-b@example.com");
    ResponseEntity<JsonNode> listB =
        restTemplate.exchange(
            "/api/v1/trainer/questions",
            HttpMethod.GET,
            new HttpEntity<>(null, bearer(tokenB)),
            JsonNode.class);
    assertThat(listB.getBody().get("content")).isEmpty();

    ResponseEntity<JsonNode> listA =
        restTemplate.exchange(
            "/api/v1/trainer/questions",
            HttpMethod.GET,
            new HttpEntity<>(null, bearer(tokenA)),
            JsonNode.class);
    assertThat(listA.getBody().get("content")).hasSize(2);
    assertThat(listA.getBody().toString()).doesNotContain("trainer-gen-b@example.com");
  }

  @Test
  void generate_unknownSourceRefId_maps422() throws Exception {
    stubLlm(
        """
        {"questions":[
          {"questionText":"What did you ask in your last onsite?","source":"OWN_LOG","sourceRefId":999}
        ]}""");

    String token = registerAndLogin("trainer-grounding@example.com");
    ResponseEntity<JsonNode> response = generate(token, Map.of("count", 1));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void generate_rolePatternNamingTargetCompany_maps422() throws Exception {
    stubLlm(
        """
        {"questions":[
          {"questionText":"How would you improve ZetaCorp's onboarding flow?","source":"ROLE_PATTERN","sourceRefId":null}
        ]}""");

    String token = registerAndLogin("trainer-grounding-company@example.com");
    ResponseEntity<JsonNode> response = generate(token, Map.of("company", "ZetaCorp", "count", 1));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void generate_llmSchemaFailureAfterRetry_maps502() {
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenThrow(new LlmOutputException(LlmTask.TAILORING, List.of("missing questions field")));

    String token = registerAndLogin("trainer-schema@example.com");
    ResponseEntity<JsonNode> response = generate(token, Map.of("count", 1));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
  }

  private void stubLlm(String json) throws Exception {
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenReturn(new LlmGateway.LlmJsonResult(objectMapper.readTree(json), "test-model", 40));
  }

  private ResponseEntity<JsonNode> generate(String token, Map<String, Object> body) {
    return restTemplate.exchange(
        "/api/v1/trainer/generate",
        HttpMethod.POST,
        new HttpEntity<>(body, bearer(token)),
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

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
