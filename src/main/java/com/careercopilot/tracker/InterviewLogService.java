package com.careercopilot.tracker;

import com.careercopilot.auth.User;
import com.careercopilot.tracker.dto.InterviewLogRequest;
import com.careercopilot.tracker.dto.InterviewLogResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Interview-log CRUD scoped to an owned application. */
@Service
@Transactional
public class InterviewLogService {

  private final InterviewLogRepository interviewLogRepository;
  private final ApplicationService applicationService;

  public InterviewLogService(
      InterviewLogRepository interviewLogRepository, ApplicationService applicationService) {
    this.interviewLogRepository = interviewLogRepository;
    this.applicationService = applicationService;
  }

  @Transactional(readOnly = true)
  public List<InterviewLogResponse> list(String email, Long applicationId) {
    User user = applicationService.resolveUser(email);
    applicationService.ownedApplication(user, applicationId);
    return interviewLogRepository
        .findByApplicationIdAndUserIdOrderByRoundNumberAsc(applicationId, user.getId())
        .stream()
        .map(this::toResponse)
        .toList();
  }

  public InterviewLogResponse add(String email, Long applicationId, InterviewLogRequest request) {
    User user = applicationService.resolveUser(email);
    applicationService.ownedApplication(user, applicationId);

    int roundNumber = request.roundNumber() != null ? request.roundNumber() : 1;
    InterviewLog saved =
        interviewLogRepository.save(
            new InterviewLog(
                applicationId,
                user.getId(),
                request.roundType(),
                roundNumber,
                request.interviewDate(),
                request.durationMinutes(),
                request.interviewerRole(),
                request.questionsAsked(),
                request.notes(),
                request.feedbackReceived(),
                request.outcome(),
                request.difficulty()));
    return toResponse(saved);
  }

  private InterviewLogResponse toResponse(InterviewLog log) {
    return new InterviewLogResponse(
        log.getId(),
        log.getApplicationId(),
        log.getRoundType(),
        log.getRoundNumber(),
        log.getInterviewDate(),
        log.getDurationMinutes(),
        log.getInterviewerRole(),
        log.getQuestionsAsked(),
        log.getNotes(),
        log.getFeedbackReceived(),
        log.getOutcome(),
        log.getDifficulty(),
        log.getContributedAt(),
        log.getContributionStatus(),
        log.getCreatedAt(),
        log.getUpdatedAt());
  }
}
