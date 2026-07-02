package com.careercopilot.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.careercopilot.shared.EmbeddingGateway;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SemanticAugmenterTest {

  private final EmbeddingGateway embeddingGateway = mock(EmbeddingGateway.class);
  private final UUID userId = UUID.randomUUID();

  private static float[] vector(float x, float y) {
    return new float[] {x, y};
  }

  @Test
  void findNearMisses_flagsChunksAtOrAboveThreshold() {
    SemanticAugmenter augmenter = new SemanticAugmenter(embeddingGateway, true, 0.75);
    // "Kubernetes" is close to the orchestration chunk (cos=1.0), far from the other (cos=0).
    when(embeddingGateway.embed(eq(userId), any(), eq("container orchestration")))
        .thenReturn(vector(1, 0));
    when(embeddingGateway.embed(eq(userId), any(), eq("wrote documentation")))
        .thenReturn(vector(0, 1));
    when(embeddingGateway.embed(eq(userId), any(), eq("Kubernetes"))).thenReturn(vector(1, 0));
    when(embeddingGateway.embed(eq(userId), any(), eq("gRPC"))).thenReturn(vector(0, 1));

    List<SemanticAugmenter.NearMiss> nearMisses =
        augmenter.findNearMisses(
            userId,
            List.of("Kubernetes", "gRPC"),
            List.of("container orchestration", "wrote documentation"));

    // Both keywords have a best cosine of 1.0 against some chunk, so both are near-misses,
    // each paired with its own best chunk.
    assertThat(nearMisses).hasSize(2);
    assertThat(nearMisses)
        .anySatisfy(
            n -> {
              assertThat(n.skill()).isEqualTo("Kubernetes");
              assertThat(n.suggestedPhrase()).isEqualTo("container orchestration");
              assertThat(n.similarity()).isEqualTo(1.0);
            });
    assertThat(nearMisses)
        .anySatisfy(n -> assertThat(n.suggestedPhrase()).isEqualTo("wrote documentation"));
  }

  @Test
  void findNearMisses_excludesBelowThreshold() {
    SemanticAugmenter augmenter = new SemanticAugmenter(embeddingGateway, true, 0.75);
    when(embeddingGateway.embed(eq(userId), any(), eq("chunk"))).thenReturn(vector(1, 0));
    // cos(45°) ≈ 0.7071 < 0.75
    when(embeddingGateway.embed(eq(userId), any(), eq("Kubernetes"))).thenReturn(vector(1, 1));

    assertThat(augmenter.findNearMisses(userId, List.of("Kubernetes"), List.of("chunk")))
        .isEmpty();
  }

  @Test
  void findNearMisses_disabledSkipsEmbeddingEntirely() {
    SemanticAugmenter augmenter = new SemanticAugmenter(embeddingGateway, false, 0.75);

    assertThat(augmenter.findNearMisses(userId, List.of("Kubernetes"), List.of("chunk")))
        .isEmpty();
    verifyNoInteractions(embeddingGateway);
  }

  @Test
  void findNearMisses_emptyInputsReturnEmpty() {
    SemanticAugmenter augmenter = new SemanticAugmenter(embeddingGateway, true, 0.75);
    assertThat(augmenter.findNearMisses(userId, List.of(), List.of("chunk"))).isEmpty();
    assertThat(augmenter.findNearMisses(userId, List.of("Kubernetes"), List.of())).isEmpty();
    verifyNoInteractions(embeddingGateway);
  }

  @Test
  void cosine_handlesZeroVectors() {
    assertThat(SemanticAugmenter.cosine(vector(0, 0), vector(1, 0))).isZero();
  }
}
