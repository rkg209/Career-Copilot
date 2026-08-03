package com.careercopilot.trainer.dto;

import jakarta.validation.constraints.NotBlank;

public record AnswerRequest(@NotBlank String answerText) {}
