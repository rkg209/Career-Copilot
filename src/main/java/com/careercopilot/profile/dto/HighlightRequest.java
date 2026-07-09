package com.careercopilot.profile.dto;

import com.careercopilot.profile.HighlightType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HighlightRequest(
    @NotNull HighlightType type,
    @NotBlank @Size(max = 300) String text,
    String yearLabel,
    Integer displayOrder) {}
