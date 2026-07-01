package com.careercopilot.shared;

/** Maps to HTTP 403 — the resource exists but belongs to another user. */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException(String message) {
    super(message);
  }
}
