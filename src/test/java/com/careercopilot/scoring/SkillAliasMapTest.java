package com.careercopilot.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillAliasMapTest {

  private final SkillAliasMap aliasMap = new SkillAliasMap();

  @Test
  void baseNormalize_lowercasesAndCollapsesPunctuation() {
    assertThat(SkillAliasMap.baseNormalize("  Node.js / Express!  ")).isEqualTo("node js express");
    assertThat(SkillAliasMap.baseNormalize("C++")).isEqualTo("c++");
    assertThat(SkillAliasMap.baseNormalize("C#")).isEqualTo("c#");
    assertThat(SkillAliasMap.baseNormalize(null)).isEmpty();
  }

  @Test
  void normalize_mapsCuratedAliasesToCanonicalForm() {
    assertThat(aliasMap.normalize("K8s")).isEqualTo("kubernetes");
    assertThat(aliasMap.normalize("JS")).isEqualTo("javascript");
    assertThat(aliasMap.normalize("Node.js")).isEqualTo("nodejs");
    assertThat(aliasMap.normalize("CI/CD")).isEqualTo("cicd");
    assertThat(aliasMap.normalize("Postgres")).isEqualTo("postgresql");
  }

  @Test
  void normalize_passesUnknownTermsThroughBaseNormalization() {
    assertThat(aliasMap.normalize("Spring Boot")).isEqualTo("spring boot");
    assertThat(aliasMap.normalize("Kafka")).isEqualTo("kafka");
  }

  @Test
  void normalizeAll_dedupesAcrossAliases() {
    assertThat(aliasMap.normalizeAll(List.of("K8s", "Kubernetes", "kube", "", "  ")))
        .containsExactly("kubernetes");
  }
}
