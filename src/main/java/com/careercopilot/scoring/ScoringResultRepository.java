package com.careercopilot.scoring;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoringResultRepository extends JpaRepository<ScoringResult, Long> {

  Optional<ScoringResult> findFirstByResumeVersionIdAndUserIdOrderByScoredAtDescIdDesc(
      Long resumeVersionId, UUID userId);
}
