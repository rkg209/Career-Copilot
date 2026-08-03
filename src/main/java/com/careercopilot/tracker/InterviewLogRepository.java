package com.careercopilot.tracker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewLogRepository extends JpaRepository<InterviewLog, Long> {

  List<InterviewLog> findByApplicationIdAndUserIdOrderByRoundNumberAsc(
      Long applicationId, UUID userId);

  Optional<InterviewLog> findByIdAndApplicationIdAndUserId(
      Long id, Long applicationId, UUID userId);

  List<InterviewLog> findByApplicationIdInAndContributionStatus(
      List<Long> applicationIds, ContributionStatus contributionStatus);

  /** Own-log seed material for trainer question generation (owner-scoped). */
  List<InterviewLog> findByUserId(UUID userId);

  List<InterviewLog> findByApplicationIdIn(List<Long> applicationIds);
}
