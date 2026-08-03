package com.careercopilot.trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.LlmTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Answer grading + SM-2 progression + ownership, and the due-review queue (only the caller's due
 * cards, not-yet-due excluded, new-card backfill).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrainerAnswerAndQueueIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private MockQuestionRepository mockQuestionRepository;
  @Autowired private PracticeAnswerRepository practiceAnswerRepository;
  @Autowired private ReviewCardRepository reviewCardRepository;

  @MockitoBean private LlmGateway llmGateway;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    practiceAnswerRepository.deleteAll();
    reviewCardRepository.deleteAll();
    mockQuestionRepository.deleteAll();
  }

  @Test
  void answer_gradesPersistsAndAdvancesTheReviewCard() throws Exception {
    String token = registerAndLogin("trainer-answer@example.com");
    Long questionId = generateOneQuestion(token);

    stubRubric(5, "Clear and structured.");
    ResponseEntity<JsonNode> first = answer(token, questionId, "My answer text.");
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(first.getBody().get("qualityGrade").asInt()).isEqualTo(5);
    assertThat(first.getBody().get("nextReview").get("intervalDays").asInt()).isEqualTo(1);

    stubRubric(5, "Even better this time.");
    ResponseEntity<JsonNode> second = answer(token, questionId, "A better answer.");
    assertThat(second.getBody().get("nextReview").get("intervalDays").asInt()).isEqualTo(6);
    assertThat(second.getBody().get("nextReview").get("easeFactor").asDouble()).isGreaterThan(2.50);
  }

  @Test
  void answer_wrongOwner_returns403_andUnknownQuestion_returns404() throws Exception {
    String owner = registerAndLogin("trainer-owner@example.com");
    Long questionId = generateOneQuestion(owner);

    String other = registerAndLogin("trainer-other@example.com");
    stubRubric(3, "n/a");
    ResponseEntity<JsonNode> forbidden = answer(other, questionId, "trying to answer");
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<JsonNode> notFound = answer(owner, 999_999L, "no such question");
    assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void dueQueue_excludesJustAnsweredCardAndBackfillsUnansweredQuestions() throws Exception {
    String token = registerAndLogin("trainer-queue@example.com");
    Long answered = generateOneQuestion(token);
    Long unanswered = generateOneQuestion(token);

    stubRubric(5, "Good.");
    answer(token, answered, "answer text");

    ResponseEntity<JsonNode> due =
        restTemplate.exchange(
            "/api/v1/trainer/review/due?limit=20",
            HttpMethod.GET,
            new HttpEntity<>(null, bearer(token)),
            JsonNode.class);
    assertThat(due.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode content = due.getBody().get("content");
    boolean containsAnswered =
        content.findValuesAsText("mockQuestionId").contains(String.valueOf(answered));
    boolean containsUnanswered =
        content.findValuesAsText("mockQuestionId").contains(String.valueOf(unanswered));
    assertThat(containsAnswered).isFalse();
    assertThat(containsUnanswered).isTrue();
  }

  private Long generateOneQuestion(String token) throws Exception {
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenReturn(
            new LlmGateway.LlmJsonResult(
                objectMapper.readTree(
                    """
                    {"questions":[{"questionText":"Tell me about a recent bug you fixed.","source":"ROLE_PATTERN","sourceRefId":null}]}"""),
                "test-model",
                40));
    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            "/api/v1/trainer/generate",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("count", 1), bearer(token)),
            JsonNode.class);
    return response.getBody().get(0).get("id").asLong();
  }

  private void stubRubric(int grade, String feedback) throws Exception {
    when(llmGateway.callForJson(
            any(),
            org.mockito.ArgumentMatchers.eq(LlmTask.SCORING_AUGMENTATION),
            anyString(),
            anyString(),
            anyString(),
            any()))
        .thenReturn(
            new LlmGateway.LlmJsonResult(
                objectMapper.readTree(
                    "{\"grade\":" + grade + ",\"feedback\":\"" + feedback + "\"}"),
                "test-model",
                20));
  }

  private ResponseEntity<JsonNode> answer(String token, Long questionId, String answerText) {
    return restTemplate.exchange(
        "/api/v1/trainer/questions/" + questionId + "/answer",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("answerText", answerText), bearer(token)),
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
