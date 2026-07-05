package com.careercopilot.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.scoring.SkillAliasMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillExtractorTest {

  private final SkillExtractor extractor = new SkillExtractor(new SkillAliasMap());

  @Test
  void extractsCanonicalSkillsFromDescription() {
    List<String> skills =
        extractor.extract(
            "Machine Learning Engineer",
            "You will build systems with Python, PyTorch, and AWS at scale.");
    assertThat(skills)
        .contains("python", "pytorch", "aws", "machine learning")
        .doesNotHaveDuplicates();
  }

  @Test
  void normalizesAliasesAndDeduplicates() {
    // "JS" and "JavaScript" both normalize to "javascript"; "K8s" -> "kubernetes".
    List<String> skills =
        extractor.extract("Frontend Dev", "Strong in JS and JavaScript, plus K8s.");
    assertThat(skills).contains("javascript", "kubernetes");
    assertThat(skills.stream().filter("javascript"::equals).count()).isEqualTo(1);
  }

  @Test
  void returnsEmptyForSkilllessText() {
    assertThat(extractor.extract("Office Manager", "Coordinate schedules and answer phones."))
        .isEmpty();
  }

  @Test
  void nullSafe() {
    assertThat(extractor.extract(null, null)).isEmpty();
    assertThat(extractor.extract("Engineer", null)).isEmpty();
  }
}
