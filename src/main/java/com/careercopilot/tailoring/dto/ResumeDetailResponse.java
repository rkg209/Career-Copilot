package com.careercopilot.tailoring.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** Resume metadata plus the structured JSON payload. */
public record ResumeDetailResponse(
    Long resumeVersionId,
    String templateId,
    Long jobPostingId,
    String llmModel,
    int tokenCount,
    String renderStatus,
    String renderError,
    JsonNode jsonPayload,
    Instant createdAt) {}
