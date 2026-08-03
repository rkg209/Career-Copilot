package com.careercopilot.trainer;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.shared.ForbiddenException;
import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.LlmTask;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.shared.guardrail.PromptInjectionAdvisor;
import com.careercopilot.trainer.dto.AnswerResult;
import com.careercopilot.trainer.dto.NextReview;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grades a typed answer with an LLM rubric (0-5 + feedback), persists it, and advances the
 * question's SM-2 review card.
 */
@Service
@Transactional
public class AnswerEvaluationService {

  private final UserRepository userRepository;
  private final MockQuestionRepository mockQuestionRepository;
  private final PracticeAnswerRepository practiceAnswerRepository;
  private final ReviewCardRepository reviewCardRepository;
  private final LlmGateway llmGateway;
  private final PromptInjectionAdvisor promptInjectionAdvisor;

  public AnswerEvaluationService(
      UserRepository userRepository,
      MockQuestionRepository mockQuestionRepository,
      PracticeAnswerRepository practiceAnswerRepository,
      ReviewCardRepository reviewCardRepository,
      LlmGateway llmGateway,
      PromptInjectionAdvisor promptInjectionAdvisor) {
    this.userRepository = userRepository;
    this.mockQuestionRepository = mockQuestionRepository;
    this.practiceAnswerRepository = practiceAnswerRepository;
    this.reviewCardRepository = reviewCardRepository;
    this.llmGateway = llmGateway;
    this.promptInjectionAdvisor = promptInjectionAdvisor;
  }

  public AnswerResult evaluate(String email, Long questionId, String answerText) {
    User user = resolveUser(email);
    MockQuestion question = ownedQuestion(user.getId(), questionId);

    String fencedAnswer = promptInjectionAdvisor.fence("CANDIDATE ANSWER", answerText);
    LlmGateway.LlmJsonResult result =
        llmGateway.callForJson(
            user.getId(),
            LlmTask.SCORING_AUGMENTATION,
            TrainerPrompts.ANSWER_EVALUATION_SYSTEM_PROMPT,
            TrainerPrompts.answerEvaluationUserPrompt(question.getQuestionText(), fencedAnswer),
            "answer-evaluation",
            null);

    int grade = result.payload().path("grade").asInt();
    String feedback = result.payload().path("feedback").asText();

    PracticeAnswer savedAnswer =
        practiceAnswerRepository.save(
            new PracticeAnswer(
                user.getId(),
                question.getId(),
                answerText,
                grade,
                feedback,
                result.modelUsed(),
                result.totalTokens()));

    ReviewCard card =
        reviewCardRepository
            .findByUserIdAndMockQuestionId(user.getId(), question.getId())
            .orElseGet(() -> new ReviewCard(user.getId(), question.getId()));
    Sm2Scheduler.Sm2State nextState =
        Sm2Scheduler.update(
            card.getEaseFactor(),
            card.getIntervalDays(),
            card.getRepetitions(),
            grade,
            Instant.now());
    card.apply(nextState, grade);
    reviewCardRepository.save(card);

    return new AnswerResult(
        savedAnswer.getId(),
        question.getId(),
        grade,
        feedback,
        new NextReview(nextState.dueAt(), nextState.intervalDays(), nextState.easeFactor()));
  }

  private MockQuestion ownedQuestion(java.util.UUID userId, Long questionId) {
    MockQuestion question =
        mockQuestionRepository
            .findById(questionId)
            .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
    if (!question.getUserId().equals(userId)) {
      throw new ForbiddenException("Question belongs to another user");
    }
    return question;
  }

  private User resolveUser(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
  }
}
