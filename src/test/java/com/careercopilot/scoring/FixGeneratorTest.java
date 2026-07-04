package com.careercopilot.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FixGeneratorTest {

  private final FixGenerator fixGenerator = new FixGenerator();

  @Test
  void build_ordersNearMissesThenRequiredThenPreferred() {
    List<FixGenerator.Fix> fixes =
        fixGenerator.build(
            List.of("Kubernetes", "gRPC"),
            List.of("Terraform"),
            List.of(new SemanticAugmenter.NearMiss("Kubernetes", 0.85, "container orchestration")));

    assertThat(fixes)
        .extracting(FixGenerator.Fix::skill)
        .containsExactly("Kubernetes", "gRPC", "Terraform");
    assertThat(fixes).extracting(FixGenerator.Fix::priority).containsExactly(1, 2, 3);
    assertThat(fixes.get(0).suggestedBullet()).contains("container orchestration");
    assertThat(fixes.get(1).suggestedBullet()).contains("Required");
    assertThat(fixes.get(2).suggestedBullet()).contains("Nice-to-have");
  }

  @Test
  void build_nearMissKeywordNotDuplicatedAsPlainMissing() {
    List<FixGenerator.Fix> fixes =
        fixGenerator.build(
            List.of("Kubernetes"),
            List.of(),
            List.of(new SemanticAugmenter.NearMiss("Kubernetes", 0.9, "orchestration")));

    assertThat(fixes).hasSize(1);
  }

  @Test
  void build_emptyInputsGiveNoFixes() {
    assertThat(fixGenerator.build(List.of(), List.of(), List.of())).isEmpty();
  }

  @Test
  void build_truncatesLongSuggestedPhrases() {
    String longPhrase = "x".repeat(200);
    List<FixGenerator.Fix> fixes =
        fixGenerator.build(
            List.of("Kubernetes"),
            List.of(),
            List.of(new SemanticAugmenter.NearMiss("Kubernetes", 0.9, longPhrase)));
    assertThat(fixes.get(0).suggestedBullet()).contains("...").doesNotContain(longPhrase);
  }
}
