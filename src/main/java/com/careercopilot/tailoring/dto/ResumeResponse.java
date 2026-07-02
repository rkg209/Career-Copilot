package com.careercopilot.tailoring.dto;

import java.time.Instant;

/** Resume metadata (no PDF bytes, no JSON payload). */
public record ResumeResponse(
    Long resumeVersionId,
    String templateId,
    Long jobPostingId,
    String llmModel,
    int tokenCount,
    String renderStatus,
    Instant createdAt) {}
