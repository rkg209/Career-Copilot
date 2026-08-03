package com.careercopilot.trainer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record NextReview(Instant dueAt, int intervalDays, BigDecimal easeFactor) {}
