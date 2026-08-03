package com.careercopilot.trainer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MockQuestionRepository extends JpaRepository<MockQuestion, Long> {

  Page<MockQuestion> findByUserId(UUID userId, Pageable pageable);

  Optional<MockQuestion> findByIdAndUserId(Long id, UUID userId);

  /** Recent question texts for the generation prompt's dedupe block (also caps its size). */
  List<MockQuestion> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

  /** All of a user's questions, oldest first — used to backfill never-answered cards. */
  List<MockQuestion> findByUserIdOrderByCreatedAtAsc(UUID userId);

  List<MockQuestion> findByIdIn(List<Long> ids);
}
