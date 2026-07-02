package com.careercopilot.shared;

import java.util.List;

/** LLM output failed JSON schema validation even after one retry. Mapped to 502. */
public class LlmOutputException extends RuntimeException {

  private final LlmTask task;
  private final List<String> validationErrors;

  public LlmOutputException(LlmTask task, List<String> validationErrors) {
    super("LLM output failed schema validation for task " + task);
    this.task = task;
    this.validationErrors = validationErrors;
  }

  public LlmTask getTask() {
    return task;
  }

  public List<String> getValidationErrors() {
    return validationErrors;
  }
}
