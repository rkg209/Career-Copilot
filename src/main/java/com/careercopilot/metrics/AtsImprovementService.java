package com.careercopilot.metrics;

import com.careercopilot.metrics.dto.AtsImprovementReport;
import com.careercopilot.scoring.CoverageScorer;
import com.careercopilot.scoring.ScoringResultRepository;
import com.careercopilot.scoring.SkillAliasMap;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Computes the headline ATS-improvement metric two ways: over the deterministic 20-JD labeled set
 * (baseline vs. tailored coverage, no LLM) and over real {@code scoring_results} rows (live users).
 * Reuses the production {@link CoverageScorer}/{@link SkillAliasMap} so the math never drifts from
 * what {@code ScoringService} actually computes.
 */
@Service
public class AtsImprovementService {

  private final AtsLabeledSetLoader labeledSetLoader;
  private final CoverageScorer coverageScorer;
  private final SkillAliasMap skillAliasMap;
  private final ScoringResultRepository scoringResultRepository;

  public AtsImprovementService(
      AtsLabeledSetLoader labeledSetLoader,
      CoverageScorer coverageScorer,
      SkillAliasMap skillAliasMap,
      ScoringResultRepository scoringResultRepository) {
    this.labeledSetLoader = labeledSetLoader;
    this.coverageScorer = coverageScorer;
    this.skillAliasMap = skillAliasMap;
    this.scoringResultRepository = scoringResultRepository;
  }

  public AtsImprovementReport report() {
    return new AtsImprovementReport(computeLabeledSet(), computeLiveUsers(), Instant.now());
  }

  private AtsImprovementReport.LabeledSet computeLabeledSet() {
    List<AtsLabeledSetLoader.LabeledEntry> entries = labeledSetLoader.entries();
    double totalBaseline = 0;
    double totalTailored = 0;
    for (AtsLabeledSetLoader.LabeledEntry entry : entries) {
      Map<String, String> required = keywordMap(entry.required());
      Map<String, String> preferred = keywordMap(entry.preferred());
      totalBaseline +=
          coverageScorer
              .score(required, preferred, coverageScorer.buildTermIndex(entry.baselineResumeText()))
              .score();
      totalTailored +=
          coverageScorer
              .score(required, preferred, coverageScorer.buildTermIndex(entry.resumeText()))
              .score();
    }
    int n = entries.size();
    double meanBaseline = round2(totalBaseline / n);
    double meanTailored = round2(totalTailored / n);
    return new AtsImprovementReport.LabeledSet(
        n, meanBaseline, meanTailored, round2(meanTailored - meanBaseline));
  }

  private AtsImprovementReport.LiveUsers computeLiveUsers() {
    List<Object[]> spreads = scoringResultRepository.findScoreSpreadsPerUserAndJob();
    if (spreads.isEmpty()) {
      return new AtsImprovementReport.LiveUsers(0, 0.0);
    }
    double totalImprovement = 0;
    for (Object[] row : spreads) {
      BigDecimal min = (BigDecimal) row[0];
      BigDecimal max = (BigDecimal) row[1];
      totalImprovement += max.subtract(min).doubleValue();
    }
    return new AtsImprovementReport.LiveUsers(
        spreads.size(), round2(totalImprovement / spreads.size()));
  }

  private Map<String, String> keywordMap(List<String> terms) {
    Map<String, String> keywords = new LinkedHashMap<>();
    for (String term : terms) {
      keywords.putIfAbsent(skillAliasMap.normalize(term), term);
    }
    return keywords;
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
