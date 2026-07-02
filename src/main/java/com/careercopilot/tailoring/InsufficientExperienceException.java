package com.careercopilot.tailoring;

/** The user has no embedded experience entries to tailor from. Mapped to 422. */
public class InsufficientExperienceException extends RuntimeException {

  public InsufficientExperienceException(String message) {
    super(message);
  }
}
