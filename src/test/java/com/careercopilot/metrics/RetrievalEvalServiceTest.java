package com.careercopilot.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RetrievalEvalServiceTest {

  private final RetrievalEvalService service = new RetrievalEvalService();

  @Test
  void precisionAtK_countsRelevantHitsWithinTopK() {
    List<Long> ranked = List.of(1L, 2L, 3L, 4L, 5L);
    Set<Long> relevant = Set.of(1L, 3L, 5L);

    assertThat(service.precisionAtK(ranked, relevant, 5)).isEqualTo(3.0 / 5.0);
    assertThat(service.precisionAtK(ranked, relevant, 2)).isEqualTo(1.0 / 2.0);
  }

  @Test
  void precisionAtK_kLargerThanList_isClampedToListSize() {
    List<Long> ranked = List.of(1L, 2L);
    Set<Long> relevant = Set.of(1L, 2L);

    assertThat(service.precisionAtK(ranked, relevant, 50)).isEqualTo(1.0);
  }

  @Test
  void precisionAtK_emptyRelevantSet_isZero() {
    List<Long> ranked = List.of(1L, 2L, 3L);

    assertThat(service.precisionAtK(ranked, Set.of(), 3)).isZero();
  }

  @Test
  void precisionAtK_allRelevant_isOne() {
    List<Long> ranked = List.of(1L, 2L, 3L);
    Set<Long> relevant = Set.of(1L, 2L, 3L, 4L, 5L);

    assertThat(service.precisionAtK(ranked, relevant, 3)).isEqualTo(1.0);
  }

  @Test
  void precisionAtK_emptyRankedList_isZero() {
    assertThat(service.precisionAtK(List.of(), Set.of(1L), 5)).isZero();
  }

  @Test
  void precisionAtK_nonPositiveK_isZero() {
    assertThat(service.precisionAtK(List.of(1L, 2L), Set.of(1L), 0)).isZero();
  }

  @Test
  void meanPrecisionAtK_averagesAcrossQueries() {
    var queries =
        List.of(
            new RetrievalEvalService.QueryResult("q1", List.of(1L, 2L), Set.of(1L)),
            new RetrievalEvalService.QueryResult("q2", List.of(3L, 4L), Set.of(3L, 4L)));

    assertThat(service.meanPrecisionAtK(queries, 2)).isEqualTo((0.5 + 1.0) / 2);
  }

  @Test
  void meanPrecisionAtK_emptyQuerySet_isZero() {
    assertThat(service.meanPrecisionAtK(List.of(), 5)).isZero();
  }
}
