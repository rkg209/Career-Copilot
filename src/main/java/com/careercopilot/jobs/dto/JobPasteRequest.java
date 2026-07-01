package com.careercopilot.jobs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for a user pasting a job description manually. */
public record JobPasteRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 255) String company,
    @Size(max = 255) String location,
    @Size(max = 2048) String url,
    @NotBlank @Size(min = 50, max = 20000) String rawText) {}
