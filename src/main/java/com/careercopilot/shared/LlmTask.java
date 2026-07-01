package com.careercopilot.shared;

/** Mirrors the Postgres {@code llm_task} enum (V8 migration). */
public enum LlmTask {
  EXTRACTION,
  TAILORING,
  SCORING_AUGMENTATION,
  COVER_LETTER,
  EMBEDDING
}
