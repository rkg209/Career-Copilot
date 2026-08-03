package com.careercopilot.trainer;

import com.careercopilot.shared.PageResponse;
import com.careercopilot.trainer.dto.AnswerRequest;
import com.careercopilot.trainer.dto.AnswerResult;
import com.careercopilot.trainer.dto.DueCard;
import com.careercopilot.trainer.dto.GenerateRequest;
import com.careercopilot.trainer.dto.MockQuestionResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trainer")
public class TrainerController {

  private final QuestionGenerationService questionGenerationService;
  private final AnswerEvaluationService answerEvaluationService;
  private final ReviewQueueService reviewQueueService;
  private final MockQuestionQueryService mockQuestionQueryService;

  public TrainerController(
      QuestionGenerationService questionGenerationService,
      AnswerEvaluationService answerEvaluationService,
      ReviewQueueService reviewQueueService,
      MockQuestionQueryService mockQuestionQueryService) {
    this.questionGenerationService = questionGenerationService;
    this.answerEvaluationService = answerEvaluationService;
    this.reviewQueueService = reviewQueueService;
    this.mockQuestionQueryService = mockQuestionQueryService;
  }

  @PostMapping("/generate")
  public ResponseEntity<List<MockQuestionResponse>> generate(
      @Valid @RequestBody GenerateRequest request, Principal principal) {
    List<MockQuestionResponse> created =
        questionGenerationService.generate(principal.getName(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PostMapping("/questions/{id}/answer")
  public ResponseEntity<AnswerResult> answer(
      @PathVariable Long id, @Valid @RequestBody AnswerRequest request, Principal principal) {
    AnswerResult result =
        answerEvaluationService.evaluate(principal.getName(), id, request.answerText());
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @GetMapping("/review/due")
  public Map<String, List<DueCard>> due(
      @RequestParam(defaultValue = "20") int limit, Principal principal) {
    return Map.of("content", reviewQueueService.dueQueue(principal.getName(), limit));
  }

  @GetMapping("/questions")
  public PageResponse<MockQuestionResponse> questions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal) {
    return mockQuestionQueryService.list(principal.getName(), page, size);
  }
}
