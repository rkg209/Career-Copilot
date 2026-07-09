package com.careercopilot.profile.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** One category of skills; response carries the id, requests may omit it. */
public record SkillGroupDto(
    Long id, @NotBlank String name, List<String> skills, Integer displayOrder) {}
