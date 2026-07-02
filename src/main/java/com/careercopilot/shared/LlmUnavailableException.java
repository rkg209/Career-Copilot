package com.careercopilot.shared;

/** The chat LLM provider could not be reached or returned an error. Mapped to 503. */
public class LlmUnavailableException extends RuntimeException {

  public LlmUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
