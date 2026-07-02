package com.careercopilot.tailoring.dto;

import java.time.Instant;

/** Cover-letter metadata (mirrors ResumeResponse). */
public record CoverLetterResponse(
    Long coverLetterId,
    String templateId,
    Long jobPostingId,
    Long resumeVersionId,
    String llmModel,
    int tokenCount,
    String renderStatus,
    Instant createdAt) {}
