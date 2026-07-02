package com.careercopilot.shared;

import com.careercopilot.auth.EmailAlreadyRegisteredException;
import com.careercopilot.auth.InvalidCredentialsException;
import com.careercopilot.shared.guardrail.GroundingException;
import com.careercopilot.tailoring.InsufficientExperienceException;
import com.careercopilot.tailoring.render.RenderFailedException;
import com.careercopilot.tailoring.render.RenderTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  public ResponseEntity<ErrorResponse> handleEmailAlreadyRegistered(
      EmailAlreadyRegisteredException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler({GroundingException.class, InsufficientExperienceException.class})
  public ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(LlmOutputException.class)
  public ResponseEntity<ErrorResponse> handleLlmOutput(LlmOutputException e) {
    // 502: the upstream LLM produced unusable output even after a retry. Don't leak internals.
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(new ErrorResponse("The AI service returned an invalid response. Please try again."));
  }

  @ExceptionHandler({LlmUnavailableException.class, EmbeddingUnavailableException.class})
  public ResponseEntity<ErrorResponse> handleLlmUnavailable(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse("The AI service is temporarily unavailable. Please retry later."));
  }

  @ExceptionHandler(RenderTimeoutException.class)
  public ResponseEntity<ErrorResponse> handleRenderTimeout(RenderTimeoutException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(RenderFailedException.class)
  public ResponseEntity<ErrorResponse> handleRenderFailed(RenderFailedException e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .orElse("Validation failed");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
  }

  public record ErrorResponse(String message) {}
}
