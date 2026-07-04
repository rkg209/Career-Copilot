package com.careercopilot.scoring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Fully deterministic scoring math. A JD keyword counts as covered when its normalized form appears
 * among the resume's normalized 1–3-gram terms. Composite score (spec v1.1):
 *
 * <pre>score = (requiredCoverage*0.6 + preferredCoverage*0.25 + keywordDensity*0.15) * 100</pre>
 *
 * where keywordDensity is the fraction of ALL extracted JD keywords (required ∪ preferred, deduped
 * after normalization) present in the resume. An empty keyword set scores full coverage for its
 * component — nothing was asked for, so nothing is missing.
 */
@Component
public class CoverageScorer {

  static final double REQUIRED_WEIGHT = 0.6;
  static final double PREFERRED_WEIGHT = 0.25;
  static final double DENSITY_WEIGHT = 0.15;
  private static final int MAX_NGRAM = 3;

  private final SkillAliasMap skillAliasMap;

  public CoverageScorer(SkillAliasMap skillAliasMap) {
    this.skillAliasMap = skillAliasMap;
  }

  public record CoverageResult(
      double requiredCoverage,
      double preferredCoverage,
      double keywordDensity,
      double score,
      List<String> missingRequired,
      List<String> missingPreferred) {}

  /**
   * @param required normalized keyword → display form
   * @param preferred normalized keyword → display form
   * @param resumeTerms normalized term index from {@link #buildTermIndex(List)}
   */
  public CoverageResult score(
      Map<String, String> required, Map<String, String> preferred, Set<String> resumeTerms) {
    List<String> missingRequired = missing(required, resumeTerms);
    List<String> missingPreferred = missing(preferred, resumeTerms);

    double requiredCoverage = coverage(required.size(), missingRequired.size());
    double preferredCoverage = coverage(preferred.size(), missingPreferred.size());
    int totalKeywords = required.size() + preferred.size();
    double keywordDensity =
        coverage(totalKeywords, missingRequired.size() + missingPreferred.size());

    double score =
        (requiredCoverage * REQUIRED_WEIGHT
                + preferredCoverage * PREFERRED_WEIGHT
                + keywordDensity * DENSITY_WEIGHT)
            * 100.0;
    score = Math.round(score * 100.0) / 100.0;

    return new CoverageResult(
        requiredCoverage,
        preferredCoverage,
        keywordDensity,
        score,
        missingRequired,
        missingPreferred);
  }

  /** Builds the normalized 1–3-gram term index from resume text pieces (skills, bullets, …). */
  public Set<String> buildTermIndex(List<String> textPieces) {
    Set<String> terms = new LinkedHashSet<>();
    for (String piece : textPieces) {
      if (piece == null || piece.isBlank()) {
        continue;
      }
      String[] tokens = SkillAliasMap.baseNormalize(piece).split(" ");
      for (int start = 0; start < tokens.length; start++) {
        StringBuilder gram = new StringBuilder();
        for (int len = 1; len <= MAX_NGRAM && start + len <= tokens.length; len++) {
          if (len > 1) {
            gram.append(' ');
          }
          gram.append(tokens[start + len - 1]);
          String normalized = skillAliasMap.normalize(gram.toString());
          if (!normalized.isEmpty()) {
            terms.add(normalized);
          }
        }
      }
    }
    return terms;
  }

  private static List<String> missing(Map<String, String> keywords, Set<String> resumeTerms) {
    List<String> missing = new ArrayList<>();
    for (Map.Entry<String, String> keyword : keywords.entrySet()) {
      if (!resumeTerms.contains(keyword.getKey())) {
        missing.add(keyword.getValue());
      }
    }
    return missing;
  }

  private static double coverage(int total, int missingCount) {
    if (total == 0) {
      return 1.0;
    }
    return (double) (total - missingCount) / total;
  }
}
