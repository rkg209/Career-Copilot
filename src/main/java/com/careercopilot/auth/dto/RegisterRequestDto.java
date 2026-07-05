package com.careercopilot.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @Size(max = 120) String name) {

  /** Convenience constructor for callers (and tests) that omit the optional name. */
  public RegisterRequestDto(String email, String password) {
    this(email, password, null);
  }
}
