package com.careercopilot.profile.dto;

import com.careercopilot.profile.HighlightType;

public record HighlightResponse(
    Long id, HighlightType type, String text, String yearLabel, int displayOrder) {}
