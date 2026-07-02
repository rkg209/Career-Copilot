package com.careercopilot.scoring.dto;

import jakarta.validation.constraints.NotNull;

public record ScoreRequest(@NotNull Long resumeVersionId, @NotNull Long jobPostingId) {}
