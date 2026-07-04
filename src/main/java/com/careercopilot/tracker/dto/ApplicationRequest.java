package com.careercopilot.tracker.dto;

import com.careercopilot.tracker.ApplicationStatus;
import java.time.Instant;

/**
 * Shared shape for create ({@code POST /applications}) and update ({@code PUT /applications/{id}})
 * — on create, {@code jobTitleSnapshot}/{@code companySnapshot} default from the linked job posting
 * when omitted; on update, every field is written as-is (a full replace).
 */
public record ApplicationRequest(
    Long jobPostingId,
    String jobTitleSnapshot,
    String companySnapshot,
    Long resumeVersionId,
    Long coverLetterId,
    ApplicationStatus status,
    Instant appliedAt,
    String notes,
    String sourceUrl) {}
