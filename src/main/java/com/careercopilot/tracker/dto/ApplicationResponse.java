package com.careercopilot.tracker.dto;

import com.careercopilot.tracker.ApplicationStatus;
import java.time.Instant;

public record ApplicationResponse(
    Long id,
    Long jobPostingId,
    String jobTitleSnapshot,
    String companySnapshot,
    Long resumeVersionId,
    Long coverLetterId,
    ApplicationStatus status,
    Instant appliedAt,
    String notes,
    String sourceUrl,
    Instant createdAt,
    Instant updatedAt) {}
