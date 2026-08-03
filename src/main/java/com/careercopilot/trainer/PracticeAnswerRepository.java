package com.careercopilot.trainer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeAnswerRepository extends JpaRepository<PracticeAnswer, Long> {

  List<PracticeAnswer> findByMockQuestionIdAndUserIdOrderByAnsweredAtDesc(
      Long mockQuestionId, java.util.UUID userId);

  /** Most recent grade per question, used to join into the questions list. */
  List<PracticeAnswer> findTop1ByMockQuestionIdAndUserIdOrderByAnsweredAtDesc(
      Long mockQuestionId, java.util.UUID userId);
}
