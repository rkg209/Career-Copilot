package com.careercopilot.jobs.dto;

import java.util.List;

/** The available role presets, in display order. */
public record RolesResponse(List<RolePresetResponse> roles) {}
