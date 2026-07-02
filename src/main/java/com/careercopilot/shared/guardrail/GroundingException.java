package com.careercopilot.shared.guardrail;

import java.util.List;

/** LLM output referenced experience that is not in the user's retrieved corpus. Mapped to 422. */
public class GroundingException extends RuntimeException {

  private final List<String> violations;

  public GroundingException(List<String> violations) {
    super("Generated content could not be verified against your experience. Please try again.");
    this.violations = violations;
  }

  public List<String> getViolations() {
    return violations;
  }
}
