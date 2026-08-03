package com.careercopilot.trainer.dto;

import com.careercopilot.tracker.InterviewRoundType;
import com.careercopilot.trainer.MockQuestionSource;
import java.time.Instant;

public record MockQuestionResponse(
    Long id,
    String company,
    String roleCategory,
    InterviewRoundType roundType,
    String questionText,
    MockQuestionSource source,
    Long sourceRefId,
    Integer lastGrade,
    Instant createdAt) {}
