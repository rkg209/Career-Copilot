package com.careercopilot.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * NFR-8.3 headline-metric artifact: ranks each query's candidates by cosine similarity over
 * deterministic synthetic unit vectors (no embedding provider call, cost-free in CI), scores
 * precision@6 with the real {@link RetrievalEvalService}, and reports a per-query table. Mirrors
 * the {@code AtsEvalHarnessTest} precedent.
 */
class RetrievalEvalHarnessTest {

  private static final int K = 6;
  private static final double MEAN_PRECISION_FLOOR = 0.45;

  private final RetrievalEvalService retrievalEvalService = new RetrievalEvalService();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void labeledSet_meanPrecisionAtKMeetsFloor() throws Exception {
    JsonNode queries;
    try (InputStream in =
        new ClassPathResource("eval/retrieval-labeled-set.json").getInputStream()) {
      queries = objectMapper.readTree(in);
    }
    assertThat(queries).hasSizeGreaterThanOrEqualTo(10);

    StringBuilder report =
        new StringBuilder("\n=== Retrieval eval harness (labeled synthetic set) ===\n")
            .append(String.format("%-24s %10s%n", "queryId", "precision@" + K));

    double total = 0;
    for (JsonNode query : queries) {
      double[] queryVector = toVector(query.get("queryVector"));
      Set<Long> relevantIds = new java.util.HashSet<>();
      query.get("relevantIds").forEach(id -> relevantIds.add(id.asLong()));

      List<ScoredCandidate> scored = new ArrayList<>();
      for (JsonNode candidate : query.get("candidates")) {
        double[] vector = toVector(candidate.get("vector"));
        scored.add(
            new ScoredCandidate(
                candidate.get("id").asLong(), cosineSimilarity(queryVector, vector)));
      }
      List<Long> ranked =
          scored.stream()
              .sorted(Comparator.comparingDouble(ScoredCandidate::similarity).reversed())
              .map(ScoredCandidate::id)
              .toList();

      double precision = retrievalEvalService.precisionAtK(ranked, relevantIds, K);
      total += precision;
      report.append(String.format("%-24s %10.3f%n", query.get("queryId").asText(), precision));
    }

    double meanPrecision = total / queries.size();
    report.append(String.format("mean precision@%d = %.3f%n", K, meanPrecision));
    System.out.print(report);

    assertThat(meanPrecision).isGreaterThanOrEqualTo(MEAN_PRECISION_FLOOR);
  }

  private static double[] toVector(JsonNode array) {
    double[] vector = new double[array.size()];
    for (int i = 0; i < array.size(); i++) {
      vector[i] = array.get(i).asDouble();
    }
    return vector;
  }

  private static double cosineSimilarity(double[] a, double[] b) {
    double dot = 0;
    double normA = 0;
    double normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    if (normA == 0 || normB == 0) {
      return 0;
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  private record ScoredCandidate(long id, double similarity) {}
}
