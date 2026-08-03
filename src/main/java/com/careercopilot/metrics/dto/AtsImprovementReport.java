package com.careercopilot.metrics.dto;

import java.time.Instant;

/** The headline metric (FR-8.1/FR-8.2): before/after ATS coverage improvement. No PII. */
public record AtsImprovementReport(
    LabeledSet labeledSet, LiveUsers liveUsers, Instant generatedAt) {

  /** Computed over the 20-JD hand-labeled eval set (deterministic, no LLM). */
  public record LabeledSet(
      int jdCount,
      double meanBaselineCoverage,
      double meanTailoredCoverage,
      double meanImprovement) {}

  /**
   * Computed over real {@code scoring_results} rows, aggregated across all users — only counts and
   * a mean delta are returned, never a userId. {@code scoredResumes} counts user+job pairs with
   * more than one score run (i.e. pairs where a before/after delta actually exists).
   */
  public record LiveUsers(long scoredResumes, double meanImprovement) {}
}
