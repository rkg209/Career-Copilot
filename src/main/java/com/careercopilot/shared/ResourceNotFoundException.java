package com.careercopilot.shared;

/** Thrown when a requested resource does not exist or is inactive; mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
