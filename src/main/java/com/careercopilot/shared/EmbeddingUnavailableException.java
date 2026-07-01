package com.careercopilot.shared;

/**
 * Thrown when the embedding provider cannot be reached or fails. Callers must treat this as
 * non-fatal: the entry stays PENDING and the retry sweep re-attempts later.
 */
public class EmbeddingUnavailableException extends RuntimeException {

  public EmbeddingUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
