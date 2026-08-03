package com.careercopilot.trainer;

import com.careercopilot.shared.guardrail.GroundingException;
import com.careercopilot.trainer.dto.GeneratedQuestion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Trainer-local grounding check (NFR-7.2). Deliberately separate from the shared {@code
 * shared/guardrail/GroundingValidator}, which only understands resume {@code experience}/{@code
 * projects}/{@code positionsOfResponsibility} arrays keyed on {@code sourceEntryId} — question
 * generation has its own allow-set shape.
 */
@Component
public class GeneratedQuestionValidator {

  /**
   * Rejects any {@code OWN_LOG}/{@code PUBLIC_INTEL} question whose {@code sourceRefId} was not
   * offered to the LLM, and any {@code ROLE_PATTERN} question that carries a {@code sourceRefId} or
   * names the target company (role-pattern seeds must stay company-agnostic).
   */
  public void validate(
      List<GeneratedQuestion> questions,
      Map<MockQuestionSource, Set<Long>> allowedSources,
      String targetCompany) {
    List<String> violations = new ArrayList<>();
    for (GeneratedQuestion q : questions) {
      switch (q.source()) {
        case OWN_LOG, PUBLIC_INTEL -> {
          Set<Long> allowed = allowedSources.getOrDefault(q.source(), Set.of());
          if (q.sourceRefId() == null || !allowed.contains(q.sourceRefId())) {
            violations.add("Unknown sourceRefId " + q.sourceRefId() + " for source " + q.source());
          }
        }
        case ROLE_PATTERN -> {
          if (q.sourceRefId() != null) {
            violations.add(
                "ROLE_PATTERN question must not carry a sourceRefId: " + q.sourceRefId());
          }
          if (targetCompany != null && mentionsCompany(q.questionText(), targetCompany)) {
            violations.add(
                "ROLE_PATTERN question must be company-agnostic but named \""
                    + targetCompany
                    + "\": "
                    + q.questionText());
          }
        }
      }
    }
    if (!violations.isEmpty()) {
      throw new GroundingException(violations);
    }
  }

  private static boolean mentionsCompany(String text, String company) {
    if (text == null || company == null || company.isBlank()) {
      return false;
    }
    return text.toLowerCase().contains(company.toLowerCase());
  }
}
