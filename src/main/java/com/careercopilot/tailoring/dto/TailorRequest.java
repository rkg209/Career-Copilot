package com.careercopilot.tailoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request body for POST /api/v1/tailoring/tailor. */
public record TailorRequest(@NotNull Long jobPostingId, @NotBlank String templateId) {}
