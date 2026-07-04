package com.careercopilot.tracker.dto;

import com.careercopilot.tracker.InterviewRoundType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

public record InterviewLogRequest(
    @NotNull InterviewRoundType roundType,
    @Min(1) Integer roundNumber,
    LocalDate interviewDate,
    @Positive Integer durationMinutes,
    String interviewerRole,
    List<String> questionsAsked,
    String notes,
    String feedbackReceived,
    String outcome,
    @Min(1) @Max(5) Integer difficulty) {}
