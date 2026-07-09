package com.careercopilot.profile.dto;

import com.careercopilot.profile.EducationScoreType;

public record EducationResponse(
    Long id,
    String degree,
    String institution,
    String university,
    String specialization,
    Integer startYear,
    Integer endYear,
    EducationScoreType scoreType,
    String scoreValue,
    int displayOrder) {}
