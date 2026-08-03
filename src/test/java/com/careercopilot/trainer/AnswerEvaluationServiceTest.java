package com.careercopilot.trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.guardrail.PromptInjectionAdvisor;
import com.careercopilot.tracker.InterviewRoundType;
import com.careercopilot.trainer.dto.AnswerResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AnswerEvaluationServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final MockQuestionRepository mockQuestionRepository = mock(MockQuestionRepository.class);
  private final PracticeAnswerRepository practiceAnswerRepository =
      mock(PracticeAnswerRepository.class);
  private final ReviewCardRepository reviewCardRepository = mock(ReviewCardRepository.class);
  private final LlmGateway llmGateway = mock(LlmGateway.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final AnswerEvaluationService service =
      new AnswerEvaluationService(
          userRepository,
          mockQuestionRepository,
          practiceAnswerRepository,
          reviewCardRepository,
          llmGateway,
          new PromptInjectionAdvisor());

  private final UUID userId = UUID.randomUUID();

  @Test
  void evaluate_parsesRubricAndInvokesSchedulerForANewCard() throws Exception {
    User user = new User("candidate@example.com", "hash");
    ReflectionTestUtils.setField(user, "id", userId);
    when(userRepository.findByEmail("candidate@example.com")).thenReturn(Optional.of(user));

    MockQuestion question =
        new MockQuestion(
            userId,
            "Acme",
            "SWE",
            InterviewRoundType.TECHNICAL,
            "Explain a linked list",
            MockQuestionSource.OWN_LOG,
            1L);
    ReflectionTestUtils.setField(question, "id", 7L);
    when(mockQuestionRepository.findById(7L)).thenReturn(Optional.of(question));

    when(reviewCardRepository.findByUserIdAndMockQuestionId(userId, 7L))
        .thenReturn(Optional.empty());
    when(practiceAnswerRepository.save(any(PracticeAnswer.class)))
        .thenAnswer(
            invocation -> {
              PracticeAnswer answer = invocation.getArgument(0);
              ReflectionTestUtils.setField(answer, "id", 99L);
              return answer;
            });
    when(reviewCardRepository.save(any(ReviewCard.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    String llmJson =
        """
        {"grade":4,"feedback":"Solid explanation, missing edge cases."}""";
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenReturn(new LlmGateway.LlmJsonResult(objectMapper.readTree(llmJson), "test-model", 30));

    AnswerResult result = service.evaluate("candidate@example.com", 7L, "A linked list is...");

    assertThat(result.practiceAnswerId()).isEqualTo(99L);
    assertThat(result.mockQuestionId()).isEqualTo(7L);
    assertThat(result.qualityGrade()).isEqualTo(4);
    assertThat(result.feedback()).contains("Solid explanation");
    assertThat(result.nextReview().intervalDays()).isEqualTo(1);
    assertThat(result.nextReview().easeFactor()).isEqualTo(new BigDecimal("2.50"));

    ArgumentCaptor<ReviewCard> cardCaptor = ArgumentCaptor.forClass(ReviewCard.class);
    org.mockito.Mockito.verify(reviewCardRepository).save(cardCaptor.capture());
    assertThat(cardCaptor.getValue().getRepetitions()).isEqualTo(1);
    assertThat(cardCaptor.getValue().getLastGrade()).isEqualTo(4);
  }

  @Test
  void evaluate_answerFromAnotherUser_throwsForbidden() {
    User user = new User("attacker@example.com", "hash");
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    when(userRepository.findByEmail("attacker@example.com")).thenReturn(Optional.of(user));

    MockQuestion question =
        new MockQuestion(
            userId,
            "Acme",
            "SWE",
            InterviewRoundType.TECHNICAL,
            "Q",
            MockQuestionSource.OWN_LOG,
            1L);
    ReflectionTestUtils.setField(question, "id", 7L);
    when(mockQuestionRepository.findById(7L)).thenReturn(Optional.of(question));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.evaluate("attacker@example.com", 7L, "answer"))
        .isInstanceOf(com.careercopilot.shared.ForbiddenException.class);
  }
}
