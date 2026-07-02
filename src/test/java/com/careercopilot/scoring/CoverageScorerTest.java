package com.careercopilot.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CoverageScorerTest {

  private final SkillAliasMap aliasMap = new SkillAliasMap();
  private final CoverageScorer scorer = new CoverageScorer(aliasMap);

  private static Map<String, String> keywords(SkillAliasMap aliasMap, String... displays) {
    Map<String, String> map = new LinkedHashMap<>();
    for (String display : displays) {
      map.put(aliasMap.normalize(display), display);
    }
    return map;
  }

  @Test
  void score_isDeterministicCompositeOfCoverageAndDensity() {
    Map<String, String> required =
        keywords(aliasMap, "Java", "Spring Boot", "PostgreSQL", "Docker");
    Map<String, String> preferred = keywords(aliasMap, "Kubernetes", "Terraform");
    Set<String> resumeTerms =
        scorer.buildTermIndex(
            List.of("Java", "Spring Boot", "Built services with PostgreSQL", "Kubernetes"));

    CoverageScorer.CoverageResult result = scorer.score(required, preferred, resumeTerms);

    // required 3/4, preferred 1/2, density 4/6
    assertThat(result.requiredCoverage()).isEqualTo(0.75);
    assertThat(result.preferredCoverage()).isEqualTo(0.5);
    assertThat(result.keywordDensity()).isCloseTo(4.0 / 6.0, within(1e-9));
    assertThat(result.score())
        .isCloseTo((0.75 * 0.6 + 0.5 * 0.25 + (4.0 / 6.0) * 0.15) * 100.0, within(0.01));
    assertThat(result.missingRequired()).containsExactly("Docker");
    assertThat(result.missingPreferred()).containsExactly("Terraform");

    // Re-running with identical inputs yields the identical result (NFR-4.1).
    assertThat(scorer.score(required, preferred, resumeTerms)).isEqualTo(result);
  }

  @Test
  void score_matchesThroughAliasNormalization() {
    Map<String, String> required = keywords(aliasMap, "K8s", "CI/CD", "Postgres");
    Set<String> resumeTerms =
        scorer.buildTermIndex(
            List.of("Deployed on Kubernetes", "GitHub Actions CI/CD pipelines", "PostgreSQL"));

    CoverageScorer.CoverageResult result = scorer.score(required, Map.of(), resumeTerms);

    assertThat(result.requiredCoverage()).isEqualTo(1.0);
    assertThat(result.missingRequired()).isEmpty();
  }

  @Test
  void score_matchesMultiWordKeywordsViaNgrams() {
    Map<String, String> required = keywords(aliasMap, "machine learning", "spring boot");
    Set<String> resumeTerms =
        scorer.buildTermIndex(List.of("Applied machine learning models in Spring Boot services"));

    assertThat(scorer.score(required, Map.of(), resumeTerms).requiredCoverage()).isEqualTo(1.0);
  }

  @Test
  void score_emptyRequiredSetCountsAsFullCoverage() {
    CoverageScorer.CoverageResult result =
        scorer.score(Map.of(), keywords(aliasMap, "Terraform"), scorer.buildTermIndex(List.of()));

    assertThat(result.requiredCoverage()).isEqualTo(1.0);
    assertThat(result.preferredCoverage()).isZero();
    // score = (1.0*0.6 + 0*0.25 + 0*0.15) * 100
    assertThat(result.score()).isEqualTo(60.0);
  }

  @Test
  void score_noKeywordsAtAllScoresFull() {
    CoverageScorer.CoverageResult result =
        scorer.score(Map.of(), Map.of(), scorer.buildTermIndex(List.of("anything")));
    assertThat(result.score()).isEqualTo(100.0);
  }

  @Test
  void buildTermIndex_ignoresBlankPieces() {
    assertThat(scorer.buildTermIndex(List.of("", "   ", "Java"))).contains("java");
  }
}
