package com.careercopilot.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.careercopilot.scoring.CoverageScorer;
import com.careercopilot.scoring.ScoringResultRepository;
import com.careercopilot.scoring.SkillAliasMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AtsImprovementServiceTest {

  @Mock private ScoringResultRepository scoringResultRepository;

  private final SkillAliasMap skillAliasMap = new SkillAliasMap();
  private final CoverageScorer coverageScorer = new CoverageScorer(skillAliasMap);
  private final AtsLabeledSetLoader labeledSetLoader = new AtsLabeledSetLoader(new ObjectMapper());

  private AtsImprovementService service() {
    return new AtsImprovementService(
        labeledSetLoader, coverageScorer, skillAliasMap, scoringResultRepository);
  }

  @Test
  void labeledSet_tailoredScoresBeatBaselineScores_onTheRealEvalSet() {
    var report = service().report();

    assertThat(report.labeledSet().jdCount()).isEqualTo(labeledSetLoader.entries().size());
    assertThat(report.labeledSet().meanTailoredCoverage())
        .isGreaterThan(report.labeledSet().meanBaselineCoverage());
    assertThat(report.labeledSet().meanImprovement())
        .isEqualTo(
            round2(
                report.labeledSet().meanTailoredCoverage()
                    - report.labeledSet().meanBaselineCoverage()));
  }

  @Test
  void liveUsers_computesMeanDelta_fromSeededScoreSpreadRows() {
    when(scoringResultRepository.findScoreSpreadsPerUserAndJob())
        .thenReturn(
            List.of(
                new Object[] {new BigDecimal("40.00"), new BigDecimal("70.00")},
                new Object[] {new BigDecimal("50.00"), new BigDecimal("60.00")}));

    var report = service().report();

    assertThat(report.liveUsers().scoredResumes()).isEqualTo(2);
    // deltas: 30 and 10 -> mean 20
    assertThat(report.liveUsers().meanImprovement()).isEqualTo(20.0);
  }

  @Test
  void liveUsers_returnsZeros_whenNoScoreRunsExistYet() {
    when(scoringResultRepository.findScoreSpreadsPerUserAndJob()).thenReturn(List.of());

    var report = service().report();

    assertThat(report.liveUsers().scoredResumes()).isZero();
    assertThat(report.liveUsers().meanImprovement()).isZero();
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
