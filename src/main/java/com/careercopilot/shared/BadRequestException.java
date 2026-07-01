package com.careercopilot.shared;

/** Thrown when a request carries invalid parameters; mapped to HTTP 400 by the global handler. */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
