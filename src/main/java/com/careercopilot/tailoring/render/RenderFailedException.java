package com.careercopilot.tailoring.render;

/** PDF rendering failed (LaTeX error, missing binary, IO). Mapped to 500. */
public class RenderFailedException extends RuntimeException {

  public RenderFailedException(String message, Throwable cause) {
    super(message, cause);
  }

  public RenderFailedException(String message) {
    super(message);
  }
}
