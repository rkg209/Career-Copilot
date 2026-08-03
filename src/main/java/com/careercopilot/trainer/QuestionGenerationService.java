package com.careercopilot.trainer;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.intel.CompanyIntelEntry;
import com.careercopilot.intel.CompanyIntelRepository;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.LlmTask;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.shared.guardrail.PromptInjectionAdvisor;
import com.careercopilot.tracker.Application;
import com.careercopilot.tracker.ApplicationRepository;
import com.careercopilot.tracker.InterviewLog;
import com.careercopilot.tracker.InterviewLogRepository;
import com.careercopilot.trainer.dto.GenerateRequest;
import com.careercopilot.trainer.dto.GeneratedQuestion;
import com.careercopilot.trainer.dto.MockQuestionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the three grounded sources (own logs, published intel, role-pattern seeds), calls the
 * LLM for question text, validates the result never strays outside those sources (NFR-7.2), and
 * persists the survivors.
 */
@Service
@Transactional
public class QuestionGenerationService {

  private final UserRepository userRepository;
  private final ApplicationRepository applicationRepository;
  private final InterviewLogRepository interviewLogRepository;
  private final CompanyIntelRepository companyIntelRepository;
  private final RolePatternSeeds rolePatternSeeds;
  private final MockQuestionRepository mockQuestionRepository;
  private final LlmGateway llmGateway;
  private final PromptInjectionAdvisor promptInjectionAdvisor;
  private final GeneratedQuestionValidator generatedQuestionValidator;

  public QuestionGenerationService(
      UserRepository userRepository,
      ApplicationRepository applicationRepository,
      InterviewLogRepository interviewLogRepository,
      CompanyIntelRepository companyIntelRepository,
      RolePatternSeeds rolePatternSeeds,
      MockQuestionRepository mockQuestionRepository,
      LlmGateway llmGateway,
      PromptInjectionAdvisor promptInjectionAdvisor,
      GeneratedQuestionValidator generatedQuestionValidator) {
    this.userRepository = userRepository;
    this.applicationRepository = applicationRepository;
    this.interviewLogRepository = interviewLogRepository;
    this.companyIntelRepository = companyIntelRepository;
    this.rolePatternSeeds = rolePatternSeeds;
    this.mockQuestionRepository = mockQuestionRepository;
    this.llmGateway = llmGateway;
    this.promptInjectionAdvisor = promptInjectionAdvisor;
    this.generatedQuestionValidator = generatedQuestionValidator;
  }

  public List<MockQuestionResponse> generate(String email, GenerateRequest request) {
    User user = resolveUser(email);
    String company = request.company() != null ? request.company().trim() : null;

    List<InterviewLog> ownLogs = ownLogs(user.getId(), company, request.roundType());
    List<CompanyIntelEntry> intelEntries =
        company != null ? companyIntelRepository.findByCompanyAndPublishedTrue(company) : List.of();
    List<String> rolePatternSeedTexts =
        rolePatternSeeds.seedsFor(request.roleCategory(), request.roundType());

    Map<MockQuestionSource, Set<Long>> allowedSources = new EnumMap<>(MockQuestionSource.class);
    allowedSources.put(MockQuestionSource.OWN_LOG, ids(ownLogs, InterviewLog::getId));
    allowedSources.put(
        MockQuestionSource.PUBLIC_INTEL, ids(intelEntries, CompanyIntelEntry::getId));

    List<String> recentQuestionTexts =
        mockQuestionRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(MockQuestion::getQuestionText)
            .toList();

    String ownLogBlock = promptInjectionAdvisor.fence("OWN INTERVIEW LOGS", ownLogBlock(ownLogs));
    String intelBlock =
        promptInjectionAdvisor.fence("PUBLIC COMPANY INTEL", intelBlock(intelEntries));
    String rolePatternBlock = rolePatternBlock(rolePatternSeedTexts);

    String userPrompt =
        TrainerPrompts.questionGenerationUserPrompt(
            company,
            request.roleCategory(),
            request.roundType() != null ? request.roundType().name() : null,
            request.count(),
            ownLogBlock,
            intelBlock,
            rolePatternBlock,
            recentQuestionTexts);

    LlmGateway.LlmJsonResult result =
        llmGateway.callForJson(
            user.getId(),
            LlmTask.TAILORING,
            TrainerPrompts.QUESTION_GENERATION_SYSTEM_PROMPT,
            userPrompt,
            "mock-question-generation",
            null);

    List<GeneratedQuestion> generated = parseQuestions(result.payload());
    generatedQuestionValidator.validate(generated, allowedSources, company);

    List<MockQuestionResponse> saved = new ArrayList<>();
    for (GeneratedQuestion q : generated) {
      MockQuestion persisted =
          mockQuestionRepository.save(
              new MockQuestion(
                  user.getId(),
                  company,
                  request.roleCategory(),
                  request.roundType(),
                  q.questionText(),
                  q.source(),
                  q.sourceRefId()));
      saved.add(toResponse(persisted));
    }
    return saved;
  }

  private static MockQuestionResponse toResponse(MockQuestion question) {
    return new MockQuestionResponse(
        question.getId(),
        question.getCompany(),
        question.getRoleCategory(),
        question.getRoundType(),
        question.getQuestionText(),
        question.getSource(),
        question.getSourceRefId(),
        null,
        question.getCreatedAt());
  }

  private List<InterviewLog> ownLogs(
      UUID userId, String company, com.careercopilot.tracker.InterviewRoundType roundType) {
    List<Application> applications =
        company != null
            ? applicationRepository.findByUserIdAndCompanySnapshotIgnoreCase(userId, company)
            : applicationRepository.findByUserId(userId);
    List<Long> applicationIds = ids(applications, Application::getId).stream().toList();
    if (applicationIds.isEmpty()) {
      return List.of();
    }
    List<InterviewLog> logs = interviewLogRepository.findByApplicationIdIn(applicationIds);
    return roundType == null
        ? logs
        : logs.stream().filter(l -> l.getRoundType() == roundType).toList();
  }

  private static <T> Set<Long> ids(List<T> items, java.util.function.Function<T, Long> idFn) {
    Set<Long> result = new HashSet<>();
    for (T item : items) {
      result.add(idFn.apply(item));
    }
    return result;
  }

  private static String ownLogBlock(List<InterviewLog> logs) {
    if (logs.isEmpty()) {
      return "(none)";
    }
    StringBuilder sb = new StringBuilder();
    for (InterviewLog log : logs) {
      for (String question : log.getQuestionsAsked()) {
        sb.append("- id=").append(log.getId()).append(" | ").append(question).append('\n');
      }
    }
    return sb.length() == 0 ? "(none)" : sb.toString();
  }

  private static String intelBlock(List<CompanyIntelEntry> entries) {
    if (entries.isEmpty()) {
      return "(none)";
    }
    StringBuilder sb = new StringBuilder();
    for (CompanyIntelEntry entry : entries) {
      for (String question : entry.getQuestions()) {
        sb.append("- id=").append(entry.getId()).append(" | ").append(question).append('\n');
      }
    }
    return sb.length() == 0 ? "(none)" : sb.toString();
  }

  private static String rolePatternBlock(List<String> seeds) {
    if (seeds.isEmpty()) {
      return "(none)";
    }
    StringBuilder sb = new StringBuilder();
    seeds.forEach(s -> sb.append("- ").append(s).append('\n'));
    return sb.toString();
  }

  private List<GeneratedQuestion> parseQuestions(JsonNode payload) {
    JsonNode questions = payload.path("questions");
    List<GeneratedQuestion> result = new ArrayList<>();
    for (JsonNode item : questions) {
      MockQuestionSource source = MockQuestionSource.valueOf(item.path("source").asText());
      Long sourceRefId = item.hasNonNull("sourceRefId") ? item.path("sourceRefId").asLong() : null;
      result.add(new GeneratedQuestion(item.path("questionText").asText(), source, sourceRefId));
    }
    return result;
  }

  private User resolveUser(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
  }
}
