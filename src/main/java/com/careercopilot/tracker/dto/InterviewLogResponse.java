package com.careercopilot.tracker.dto;

import com.careercopilot.tracker.ContributionStatus;
import com.careercopilot.tracker.InterviewRoundType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InterviewLogResponse(
    Long id,
    Long applicationId,
    InterviewRoundType roundType,
    int roundNumber,
    LocalDate interviewDate,
    Integer durationMinutes,
    String interviewerRole,
    List<String> questionsAsked,
    String notes,
    String feedbackReceived,
    String outcome,
    Integer difficulty,
    Instant contributedAt,
    ContributionStatus contributionStatus,
    Instant createdAt,
    Instant updatedAt) {}
