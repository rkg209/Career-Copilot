package com.careercopilot.scoring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScoringResultRepository extends JpaRepository<ScoringResult, Long> {

  Optional<ScoringResult> findFirstByResumeVersionIdAndUserIdOrderByScoredAtDescIdDesc(
      Long resumeVersionId, UUID userId);

  /**
   * Aggregate metric only (min/max score per user+job, no identity returned) — safe across all
   * users. {@code count(s) > 1} excludes single-run pairs, which would otherwise dilute the mean
   * with structural zeros (nothing to compare against yet).
   */
  @Query(
      "select min(s.score), max(s.score) from ScoringResult s "
          + "group by s.userId, s.jobPostingId having count(s) > 1")
  List<Object[]> findScoreSpreadsPerUserAndJob();
}
