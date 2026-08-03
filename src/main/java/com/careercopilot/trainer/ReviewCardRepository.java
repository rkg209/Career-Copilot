package com.careercopilot.trainer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewCardRepository extends JpaRepository<ReviewCard, Long> {

  Optional<ReviewCard> findByUserIdAndMockQuestionId(UUID userId, Long mockQuestionId);

  List<Long> findMockQuestionIdsByUserId(UUID userId);

  /** Due cards, weakest (lowest ease) and oldest-due first. */
  @Query(
      "select c from ReviewCard c where c.userId = :userId and c.dueAt <= :now "
          + "order by c.easeFactor asc, c.dueAt asc")
  List<ReviewCard> findDue(
      @Param("userId") UUID userId,
      @Param("now") Instant now,
      org.springframework.data.domain.Pageable pageable);

  long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);
}
