package com.careercopilot.tailoring.render;

/** Tectonic did not finish within the configured timeout. Mapped to 503 (retryable). */
public class RenderTimeoutException extends RuntimeException {

  public RenderTimeoutException(String message) {
    super(message);
  }
}
