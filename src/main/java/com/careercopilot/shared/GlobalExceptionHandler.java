package com.careercopilot.shared;

import com.careercopilot.auth.EmailAlreadyRegisteredException;
import com.careercopilot.auth.InvalidCredentialsException;
import com.careercopilot.shared.guardrail.GroundingException;
import com.careercopilot.tailoring.InsufficientExperienceException;
import com.careercopilot.tailoring.render.RenderFailedException;
import com.careercopilot.tailoring.render.RenderTimeoutException;
import com.careercopilot.tracker.AlreadyContributedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  public ResponseEntity<ErrorResponse> handleEmailAlreadyRegistered(
      EmailAlreadyRegisteredException e) {
    return error(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
    return error(HttpStatus.UNAUTHORIZED, e.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
    return error(HttpStatus.FORBIDDEN, e.getMessage());
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
    return error(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
    return error(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(AlreadyContributedException.class)
  public ResponseEntity<ErrorResponse> handleAlreadyContributed(AlreadyContributedException e) {
    return error(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler({GroundingException.class, InsufficientExperienceException.class})
  public ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException e) {
    return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
  }

  @ExceptionHandler(LlmOutputException.class)
  public ResponseEntity<ErrorResponse> handleLlmOutput(LlmOutputException e) {
    // 502: the upstream LLM produced unusable output even after a retry. Don't leak internals.
    return error(
        HttpStatus.BAD_GATEWAY, "The AI service returned an invalid response. Please try again.");
  }

  @ExceptionHandler({LlmUnavailableException.class, EmbeddingUnavailableException.class})
  public ResponseEntity<ErrorResponse> handleLlmUnavailable(RuntimeException e) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "The AI service is temporarily unavailable. Please retry later.");
  }

  @ExceptionHandler(RenderTimeoutException.class)
  public ResponseEntity<ErrorResponse> handleRenderTimeout(RenderTimeoutException e) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
  }

  @ExceptionHandler(RenderFailedException.class)
  public ResponseEntity<ErrorResponse> handleRenderFailed(RenderFailedException e) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .orElse("Validation failed");
    return error(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    String traceId = MDC.get(TraceIdFilter.MDC_KEY);
    log.error("Unhandled exception (traceId={})", traceId, e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error. Reference: " + traceId);
  }

  private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(message, MDC.get(TraceIdFilter.MDC_KEY)));
  }

  public record ErrorResponse(String message, String traceId) {}
}
