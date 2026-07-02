package com.careercopilot.scoring;

import com.careercopilot.shared.EmbeddingGateway;
import com.careercopilot.shared.LlmTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Embedding-only near-miss detection: for each uncovered required keyword, finds the most similar
 * resume chunk by cosine similarity; at or above the threshold it becomes a near-miss with that
 * chunk as the suggested phrase to make explicit. No chat LLM involved.
 */
@Component
public class SemanticAugmenter {

  private final EmbeddingGateway embeddingGateway;
  private final boolean enabled;
  private final double threshold;

  public SemanticAugmenter(
      EmbeddingGateway embeddingGateway,
      @Value("${scoring.semantic-augmentation-enabled}") boolean enabled,
      @Value("${scoring.near-miss-threshold}") double threshold) {
    this.embeddingGateway = embeddingGateway;
    this.enabled = enabled;
    this.threshold = threshold;
  }

  public record NearMiss(String skill, double similarity, String suggestedPhrase) {}

  public List<NearMiss> findNearMisses(
      UUID userId, List<String> missingKeywords, List<String> resumeChunks) {
    if (!enabled || missingKeywords.isEmpty() || resumeChunks.isEmpty()) {
      return List.of();
    }
    List<float[]> chunkVectors = new ArrayList<>(resumeChunks.size());
    for (String chunk : resumeChunks) {
      chunkVectors.add(embeddingGateway.embed(userId, LlmTask.EMBEDDING, chunk));
    }

    List<NearMiss> nearMisses = new ArrayList<>();
    for (String keyword : missingKeywords) {
      float[] keywordVector = embeddingGateway.embed(userId, LlmTask.EMBEDDING, keyword);
      double bestSimilarity = -1.0;
      String bestChunk = null;
      for (int i = 0; i < chunkVectors.size(); i++) {
        double similarity = cosine(keywordVector, chunkVectors.get(i));
        if (similarity > bestSimilarity) {
          bestSimilarity = similarity;
          bestChunk = resumeChunks.get(i);
        }
      }
      if (bestSimilarity >= threshold) {
        double rounded = Math.round(bestSimilarity * 10000.0) / 10000.0;
        nearMisses.add(new NearMiss(keyword, rounded, bestChunk));
      }
    }
    nearMisses.sort(Comparator.comparingDouble(NearMiss::similarity).reversed());
    return nearMisses;
  }

  static double cosine(float[] a, float[] b) {
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
}
