package com.careercopilot.trainer.dto;

import java.time.Instant;

public record DueCard(
    Long mockQuestionId,
    String questionText,
    Instant dueAt,
    int repetitions,
    double easeFactor,
    boolean isNew) {}
