package com.careercopilot.trainer.dto;

import com.careercopilot.trainer.MockQuestionSource;

/** One item from the LLM's {@code mock-question-generation} JSON response. */
public record GeneratedQuestion(String questionText, MockQuestionSource source, Long sourceRefId) {}
