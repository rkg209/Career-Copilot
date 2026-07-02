package com.careercopilot.scoring.dto;

import java.time.Instant;
import java.util.List;

/** API shape for one score run — also the JSON persisted piecewise in scoring_results. */
public record AtsGapReport(
    Long scoringResultId,
    Long resumeVersionId,
    Long jobPostingId,
    double score,
    Breakdown breakdown,
    List<String> missingRequired,
    List<String> missingPreferred,
    List<NearMiss> nearMisses,
    List<Fix> fixes,
    Instant scoredAt) {

  public record Breakdown(
      double requiredCoverage, double preferredCoverage, double keywordDensity) {}

  public record NearMiss(String skill, double similarity, String suggestedPhrase) {}

  public record Fix(String skill, String suggestedBullet, int priority) {}
}
