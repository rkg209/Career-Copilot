package com.careercopilot.profile.dto;

import com.careercopilot.profile.EducationScoreType;
import jakarta.validation.constraints.NotBlank;

public record EducationRequest(
    @NotBlank String degree,
    @NotBlank String institution,
    String university,
    String specialization,
    Integer startYear,
    Integer endYear,
    EducationScoreType scoreType,
    String scoreValue,
    Integer displayOrder) {}
