package com.careercopilot.profile.dto;

import java.util.List;

/** Request and response shape for GET/PUT /api/v1/profile. */
public record ProfileDto(
    String fullName,
    String phone,
    String headline,
    String summary,
    String location,
    String linkedinUrl,
    String githubUrl,
    String portfolioUrl,
    List<String> targetRoles,
    List<String> targetLocations,
    Boolean openToRemote) {}
