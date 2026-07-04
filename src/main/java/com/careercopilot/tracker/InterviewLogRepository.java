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
}
