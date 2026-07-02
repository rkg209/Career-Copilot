package com.careercopilot.tailoring.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/v1/tailoring/cover-letter. {@code templateId} defaults to
 * cover-standard; {@code resumeVersionId} and {@code tone} are optional.
 */
public record CoverLetterRequest(
    @NotNull Long jobPostingId, String templateId, Long resumeVersionId, String tone) {}
