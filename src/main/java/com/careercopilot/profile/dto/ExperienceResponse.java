package com.careercopilot.profile.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExperienceResponse(
    Long id,
    String role,
    String company,
    String location,
    LocalDate startDate,
    LocalDate endDate,
    boolean isCurrent,
    String description,
    List<String> achievements,
    List<String> skillsUsed,
    String embeddingStatus,
    Instant createdAt,
    Instant updatedAt) {}
