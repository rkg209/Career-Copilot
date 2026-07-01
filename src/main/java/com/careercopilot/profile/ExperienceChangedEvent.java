package com.careercopilot.profile;

/** Published after an experience entry is created or its text content changed. */
public record ExperienceChangedEvent(Long entryId) {}
