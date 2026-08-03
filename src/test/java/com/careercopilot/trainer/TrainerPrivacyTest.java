package com.careercopilot.trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.intel.CompanyIntelRepository;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.guardrail.PromptInjectionAdvisor;
import com.careercopilot.tracker.ApplicationRepository;
import com.careercopilot.tracker.InterviewLogRepository;
import com.careercopilot.tracker.InterviewRoundType;
import com.careercopilot.trainer.dto.AnswerResult;
import com.careercopilot.trainer.dto.DueCard;
import com.careercopilot.trainer.dto.GenerateRequest;
import com.careercopilot.trainer.dto.MockQuestionResponse;
import com.careercopilot.trainer.dto.NextReview;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Mirrors {@code IntelNoUserLeakTest}: trainer response DTOs must never carry another user's
 * identity, and generation must only ever read the published intel pool.
 */
class TrainerPrivacyTest {

  private static final List<String> FORBIDDEN_SUBSTRINGS =
      List.of("userid", "applicationid", "email", "username");

  @Test
  void mockQuestionResponseDto_hasNoUserIdentifyingFields() {
    assertNoForbiddenFields(MockQuestionResponse.class);
  }

  @Test
  void dueCardDto_hasNoUserIdentifyingFields() {
    assertNoForbiddenFields(DueCard.class);
  }

  @Test
  void answerResultAndNextReviewDto_haveNoUserIdentifyingFields() {
    assertNoForbiddenFields(AnswerResult.class);
    assertNoForbiddenFields(NextReview.class);
  }

  @Test
  void generate_neverReadsUnpublishedIntel() throws Exception {
    UserRepository userRepository = mock(UserRepository.class);
    ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    InterviewLogRepository interviewLogRepository = mock(InterviewLogRepository.class);
    CompanyIntelRepository companyIntelRepository = mock(CompanyIntelRepository.class);
    RolePatternSeeds rolePatternSeeds = mock(RolePatternSeeds.class);
    MockQuestionRepository mockQuestionRepository = mock(MockQuestionRepository.class);
    LlmGateway llmGateway = mock(LlmGateway.class);
    GeneratedQuestionValidator generatedQuestionValidator = mock(GeneratedQuestionValidator.class);

    UUID userId = UUID.randomUUID();
    User user = new User("privacy@example.com", "hash");
    ReflectionTestUtils.setField(user, "id", userId);
    when(userRepository.findByEmail("privacy@example.com")).thenReturn(Optional.of(user));
    when(applicationRepository.findByUserIdAndCompanySnapshotIgnoreCase(any(), any()))
        .thenReturn(List.of());
    when(companyIntelRepository.findByCompanyAndPublishedTrue("Acme")).thenReturn(List.of());
    when(rolePatternSeeds.seedsFor(any(), any())).thenReturn(List.of());
    when(mockQuestionRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId))
        .thenReturn(List.of());

    ObjectMapper objectMapper = new ObjectMapper();
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenReturn(
            new LlmGateway.LlmJsonResult(
                objectMapper.readTree(
                    """
                    {"questions":[{"questionText":"Generic question","source":"ROLE_PATTERN","sourceRefId":null}]}"""),
                "test-model",
                10));
    when(mockQuestionRepository.save(any(MockQuestion.class)))
        .thenAnswer(
            invocation -> {
              MockQuestion q = invocation.getArgument(0);
              ReflectionTestUtils.setField(q, "id", 1L);
              return q;
            });

    QuestionGenerationService service =
        new QuestionGenerationService(
            userRepository,
            applicationRepository,
            interviewLogRepository,
            companyIntelRepository,
            rolePatternSeeds,
            mockQuestionRepository,
            llmGateway,
            new PromptInjectionAdvisor(),
            generatedQuestionValidator);

    service.generate(
        "privacy@example.com", new GenerateRequest("Acme", null, InterviewRoundType.TECHNICAL, 1));

    verify(companyIntelRepository, never()).findByCompanyAndPublishedFalse(any());
    verify(companyIntelRepository, never()).countByCompany(any());
  }

  private void assertNoForbiddenFields(Class<?> type) {
    for (Field field : type.getDeclaredFields()) {
      if (field.isSynthetic()) {
        continue;
      }
      String lowerName = field.getName().toLowerCase(Locale.ROOT);
      assertThat(FORBIDDEN_SUBSTRINGS)
          .as("field %s on %s must not be user-identifying", field.getName(), type.getSimpleName())
          .noneMatch(lowerName::contains);
    }
  }
}
