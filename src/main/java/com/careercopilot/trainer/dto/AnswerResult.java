package com.careercopilot.trainer.dto;

public record AnswerResult(
    Long practiceAnswerId,
    Long mockQuestionId,
    int qualityGrade,
    String feedback,
    NextReview nextReview) {}
