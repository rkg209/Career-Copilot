package com.careercopilot.tracker;

import com.careercopilot.auth.User;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.tracker.dto.InterviewLogRequest;
import com.careercopilot.tracker.dto.InterviewLogResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Interview-log CRUD scoped to an owned application, plus the Phase 6 contribute stub. */
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

  /**
   * Phase 5 stub: validates ownership and marks intent only. Full anonymize-and-publish behavior is
   * implemented in Phase 6.
   */
  public void contribute(String email, Long applicationId, Long logId) {
    User user = applicationService.resolveUser(email);
    applicationService.ownedApplication(user, applicationId);
    InterviewLog log = ownedLog(applicationId, logId, user.getId());
    if (log.getContributedAt() != null) {
      throw new AlreadyContributedException("Interview log already contributed: " + logId);
    }
    log.setContributedAt(Instant.now());
    log.setContributionStatus(ContributionStatus.STAGED);
  }

  private InterviewLog ownedLog(Long applicationId, Long logId, UUID userId) {
    return interviewLogRepository
        .findByIdAndApplicationIdAndUserId(logId, applicationId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Interview log not found: " + logId));
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
