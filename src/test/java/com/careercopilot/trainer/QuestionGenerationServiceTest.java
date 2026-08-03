package com.careercopilot.trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.intel.CompanyIntelEntry;
import com.careercopilot.intel.CompanyIntelRepository;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.guardrail.PromptInjectionAdvisor;
import com.careercopilot.tracker.Application;
import com.careercopilot.tracker.ApplicationRepository;
import com.careercopilot.tracker.ApplicationStatus;
import com.careercopilot.tracker.InterviewLog;
import com.careercopilot.tracker.InterviewLogRepository;
import com.careercopilot.tracker.InterviewRoundType;
import com.careercopilot.trainer.dto.GenerateRequest;
import com.careercopilot.trainer.dto.MockQuestionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class QuestionGenerationServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
  private final InterviewLogRepository interviewLogRepository = mock(InterviewLogRepository.class);
  private final CompanyIntelRepository companyIntelRepository = mock(CompanyIntelRepository.class);
  private final RolePatternSeeds rolePatternSeeds = mock(RolePatternSeeds.class);
  private final MockQuestionRepository mockQuestionRepository = mock(MockQuestionRepository.class);
  private final LlmGateway llmGateway = mock(LlmGateway.class);
  private final GeneratedQuestionValidator generatedQuestionValidator =
      mock(GeneratedQuestionValidator.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final QuestionGenerationService service =
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

  private final UUID userId = UUID.randomUUID();

  @Test
  void generate_assemblesSourcesAndPersistsTaggedQuestions() throws Exception {
    User user = new User("candidate@example.com", "hash");
    ReflectionTestUtils.setField(user, "id", userId);
    when(userRepository.findByEmail("candidate@example.com")).thenReturn(Optional.of(user));

    Application app =
        new Application(
            userId, null, "SWE", "Acme", null, null, ApplicationStatus.SAVED, null, null, null);
    ReflectionTestUtils.setField(app, "id", 10L);
    when(applicationRepository.findByUserIdAndCompanySnapshotIgnoreCase(userId, "Acme"))
        .thenReturn(List.of(app));

    InterviewLog log =
        new InterviewLog(
            10L,
            userId,
            InterviewRoundType.TECHNICAL,
            1,
            null,
            null,
            null,
            List.of("Tell me about yourself"),
            null,
            null,
            null,
            null);
    ReflectionTestUtils.setField(log, "id", 100L);
    when(interviewLogRepository.findByApplicationIdIn(List.of(10L))).thenReturn(List.of(log));

    CompanyIntelEntry intelEntry =
        new CompanyIntelEntry(
            "Acme",
            "SWE",
            InterviewRoundType.TECHNICAL,
            List.of("Explain caching"),
            null,
            null,
            null);
    ReflectionTestUtils.setField(intelEntry, "id", 50L);
    when(companyIntelRepository.findByCompanyAndPublishedTrue("Acme"))
        .thenReturn(List.of(intelEntry));

    when(rolePatternSeeds.seedsFor(any(), any())).thenReturn(List.of("Generic seed question"));

    MockQuestion previous =
        new MockQuestion(
            userId,
            "Acme",
            "SWE",
            InterviewRoundType.TECHNICAL,
            "Old question",
            MockQuestionSource.OWN_LOG,
            100L);
    when(mockQuestionRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId))
        .thenReturn(List.of(previous));

    String llmJson =
        """
        {"questions":[
          {"questionText":"Tell me about yourself","source":"OWN_LOG","sourceRefId":100},
          {"questionText":"Explain caching","source":"PUBLIC_INTEL","sourceRefId":50},
          {"questionText":"Generic seed question","source":"ROLE_PATTERN","sourceRefId":null}
        ]}""";
    when(llmGateway.callForJson(any(), any(), anyString(), anyString(), anyString(), any()))
        .thenReturn(new LlmGateway.LlmJsonResult(objectMapper.readTree(llmJson), "test-model", 50));

    when(mockQuestionRepository.save(any(MockQuestion.class)))
        .thenAnswer(
            invocation -> {
              MockQuestion q = invocation.getArgument(0);
              ReflectionTestUtils.setField(q, "id", 1L);
              return q;
            });

    List<MockQuestionResponse> result =
        service.generate(
            "candidate@example.com",
            new GenerateRequest("Acme", "SWE", InterviewRoundType.TECHNICAL, 3));

    assertThat(result).hasSize(3);
    assertThat(result.get(0).source()).isEqualTo(MockQuestionSource.OWN_LOG);
    assertThat(result.get(0).sourceRefId()).isEqualTo(100L);
    assertThat(result.get(1).source()).isEqualTo(MockQuestionSource.PUBLIC_INTEL);
    assertThat(result.get(1).sourceRefId()).isEqualTo(50L);
    assertThat(result.get(2).source()).isEqualTo(MockQuestionSource.ROLE_PATTERN);
    assertThat(result.get(2).sourceRefId()).isNull();

    ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(llmGateway)
        .callForJson(any(), any(), anyString(), userPrompt.capture(), anyString(), any());
    assertThat(userPrompt.getValue()).contains("Questions already asked").contains("Old question");
  }
}
