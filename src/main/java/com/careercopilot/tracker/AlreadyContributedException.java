package com.careercopilot.tracker;

/** Thrown when a log's contribute endpoint is called more than once; mapped to HTTP 409. */
public class AlreadyContributedException extends RuntimeException {

  public AlreadyContributedException(String message) {
    super(message);
  }
}
