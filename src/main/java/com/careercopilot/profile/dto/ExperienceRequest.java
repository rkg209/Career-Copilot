package com.careercopilot.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record ExperienceRequest(
    @NotBlank String role,
    @NotBlank String company,
    String location,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    Boolean isCurrent,
    String description,
    List<String> achievements,
    List<String> skillsUsed) {}
