package com.careercopilot.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The Phase 4 headline-metric artifact: scores the hand-labeled 20-JD set (FR-4.5) with recorded
 * extraction outputs (the required/preferred lists in the resource — no LLM call, deterministic and
 * cost-free in CI) through the real normalization + coverage pipeline, and reports the mean score
 * delta against the labels. Deltas indicate a regression in normalization or scoring math.
 */
class AtsEvalHarnessTest {

  private static final double MEAN_DELTA_TOLERANCE = 2.0;
  private static final double MAX_DELTA_TOLERANCE = 5.0;

  private final SkillAliasMap aliasMap = new SkillAliasMap();
  private final CoverageScorer scorer = new CoverageScorer(aliasMap);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void labeledSet_meanScoreDeltaWithinTolerance() throws Exception {
    JsonNode entries;
    try (InputStream in = new ClassPathResource("eval/ats-labeled-set.json").getInputStream()) {
      entries = objectMapper.readTree(in);
    }
    assertThat(entries).hasSizeGreaterThanOrEqualTo(20);

    double totalDelta = 0;
    double maxDelta = 0;
    StringBuilder report =
        new StringBuilder("\n=== ATS eval harness (labeled 20-JD set) ===\n")
            .append(String.format("%-8s %-32s %8s %8s %7s%n", "id", "title", "labeled", "scored", "delta"));

    for (JsonNode entry : entries) {
      Map<String, String> required = keywordMap(entry.get("required"));
      Map<String, String> preferred = keywordMap(entry.get("preferred"));
      List<String> resumeText = new java.util.ArrayList<>();
      entry.get("resumeText").forEach(piece -> resumeText.add(piece.asText()));

      double scored =
          scorer.score(required, preferred, scorer.buildTermIndex(resumeText)).score();
      double labeled = entry.get("labeledScore").asDouble();
      double delta = Math.abs(scored - labeled);
      totalDelta += delta;
      maxDelta = Math.max(maxDelta, delta);
      report.append(
          String.format(
              "%-8s %-32s %8.2f %8.2f %7.2f%n",
              entry.get("id").asText(), entry.get("title").asText(), labeled, scored, delta));
    }

    double meanDelta = totalDelta / entries.size();
    report.append(String.format("mean delta = %.3f, max delta = %.3f%n", meanDelta, maxDelta));
    System.out.print(report);

    assertThat(meanDelta).isLessThanOrEqualTo(MEAN_DELTA_TOLERANCE);
    assertThat(maxDelta).isLessThanOrEqualTo(MAX_DELTA_TOLERANCE);
  }

  private Map<String, String> keywordMap(JsonNode terms) {
    Map<String, String> keywords = new LinkedHashMap<>();
    for (JsonNode term : terms) {
      keywords.putIfAbsent(aliasMap.normalize(term.asText()), term.asText());
    }
    return keywords;
  }
}
