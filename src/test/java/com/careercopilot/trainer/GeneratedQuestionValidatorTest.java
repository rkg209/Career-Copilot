package com.careercopilot.trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.careercopilot.shared.guardrail.GroundingException;
import com.careercopilot.trainer.dto.GeneratedQuestion;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeneratedQuestionValidatorTest {

  private final GeneratedQuestionValidator validator = new GeneratedQuestionValidator();

  private Map<MockQuestionSource, Set<Long>> allowedSources(
      Set<Long> ownLogIds, Set<Long> intelIds) {
    Map<MockQuestionSource, Set<Long>> map = new EnumMap<>(MockQuestionSource.class);
    map.put(MockQuestionSource.OWN_LOG, ownLogIds);
    map.put(MockQuestionSource.PUBLIC_INTEL, intelIds);
    return map;
  }

  @Test
  void validate_cleanBatch_passes() {
    List<GeneratedQuestion> questions =
        List.of(
            new GeneratedQuestion("Explain a linked list", MockQuestionSource.OWN_LOG, 1L),
            new GeneratedQuestion("Design a rate limiter", MockQuestionSource.PUBLIC_INTEL, 5L),
            new GeneratedQuestion(
                "Walk through your approach to system design",
                MockQuestionSource.ROLE_PATTERN,
                null));

    validator.validate(questions, allowedSources(Set.of(1L), Set.of(5L)), "Acme");
  }

  @Test
  void validate_unknownSourceRefId_throwsGroundingException() {
    List<GeneratedQuestion> questions =
        List.of(new GeneratedQuestion("Explain X", MockQuestionSource.OWN_LOG, 999L));

    assertThatThrownBy(
            () -> validator.validate(questions, allowedSources(Set.of(1L), Set.of()), null))
        .isInstanceOf(GroundingException.class);
  }

  @Test
  void validate_rolePatternWithSourceRefId_throwsGroundingException() {
    List<GeneratedQuestion> questions =
        List.of(new GeneratedQuestion("Generic question", MockQuestionSource.ROLE_PATTERN, 3L));

    assertThatThrownBy(
            () -> validator.validate(questions, allowedSources(Set.of(), Set.of()), null))
        .isInstanceOf(GroundingException.class);
  }

  @Test
  void validate_rolePatternNamingTargetCompany_throwsGroundingException() {
    List<GeneratedQuestion> questions =
        List.of(
            new GeneratedQuestion(
                "How would you improve Acme Corp's infra?", MockQuestionSource.ROLE_PATTERN, null));

    assertThatThrownBy(
            () -> validator.validate(questions, allowedSources(Set.of(), Set.of()), "Acme Corp"))
        .isInstanceOf(GroundingException.class);
  }

  @Test
  void validate_violationsListedOnException() {
    List<GeneratedQuestion> questions =
        List.of(new GeneratedQuestion("Explain X", MockQuestionSource.OWN_LOG, 999L));

    try {
      validator.validate(questions, allowedSources(Set.of(1L), Set.of()), null);
    } catch (GroundingException e) {
      assertThat(e.getViolations()).isNotEmpty();
    }
  }
}
