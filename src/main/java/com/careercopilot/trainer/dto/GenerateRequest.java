package com.careercopilot.trainer.dto;

import com.careercopilot.tracker.InterviewRoundType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GenerateRequest(
    String company,
    String roleCategory,
    InterviewRoundType roundType,
    @NotNull @Min(1) @Max(25) Integer count) {}
