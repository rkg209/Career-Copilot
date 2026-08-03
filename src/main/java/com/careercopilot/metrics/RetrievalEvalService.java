package com.careercopilot.metrics;

import java.util.List;
import java.util.Set;

/**
 * Pure, Spring-free retrieval quality math (NFR-8.3) — precision@k over a ranked id list against a
 * relevant-id set. Offline/report only, not exposed over HTTP: a runtime endpoint would need live
 * embeddings computed per request, which is exactly what {@code RetrievalEvalHarnessTest} does
 * against a labeled synthetic set (mirrors the {@code AtsEvalHarnessTest} precedent).
 */
public class RetrievalEvalService {

  /**
   * Fraction of the top-{@code k} ranked ids that are relevant. Returns 0.0 for an empty ranked
   * list or {@code k <= 0}; a {@code k} larger than the ranked list is clamped to its size.
   */
  public double precisionAtK(List<Long> ranked, Set<Long> relevant, int k) {
    if (ranked.isEmpty() || k <= 0) {
      return 0.0;
    }
    int effectiveK = Math.min(k, ranked.size());
    long hits = ranked.stream().limit(effectiveK).filter(relevant::contains).count();
    return (double) hits / effectiveK;
  }

  /** Mean precision@k across a labeled set of (ranked, relevant) query results. */
  public double meanPrecisionAtK(List<QueryResult> queries, int k) {
    if (queries.isEmpty()) {
      return 0.0;
    }
    double total = 0;
    for (QueryResult query : queries) {
      total += precisionAtK(query.ranked(), query.relevant(), k);
    }
    return total / queries.size();
  }

  public record QueryResult(String queryId, List<Long> ranked, Set<Long> relevant) {}
}
