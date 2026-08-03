package com.careercopilot.shared.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

/**
 * Hardening corpus (NFR-8.5): a batch of injection payloads that {@link PromptInjectionAdvisor}
 * must neutralize, plus a control corpus that must survive byte-identical — false positives on
 * legitimate JD text are the regression that actually bites in practice.
 */
class InjectionCorpusTest {

  private final PromptInjectionAdvisor advisor = new PromptInjectionAdvisor();

  @ParameterizedTest(name = "{0}")
  @MethodSource("attacks")
  void sanitize_neutralizesEveryAttackPayload(String name, String text) {
    String sanitized = advisor.sanitize(text);
    assertThat(sanitized).as("attack '%s' must be altered by sanitize()", name).isNotEqualTo(text);
    assertThat(sanitized)
        .as("attack '%s' must be visibly neutralized", name)
        .contains("[CONTENT_REMOVED]");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("controls")
  void sanitize_leavesControlCorpusByteIdentical(String text) {
    assertThat(advisor.sanitize(text)).isEqualTo(text);
  }

  static Stream<Arguments> attacks() throws Exception {
    JsonNode root = corpus();
    List<Arguments> args = new ArrayList<>();
    for (JsonNode attack : root.get("attacks")) {
      args.add(Arguments.of(attack.get("name").asText(), attack.get("text").asText()));
    }
    return args.stream();
  }

  static Stream<Arguments> controls() throws Exception {
    JsonNode root = corpus();
    List<Arguments> args = new ArrayList<>();
    for (JsonNode control : root.get("controls")) {
      args.add(Arguments.of(control.asText()));
    }
    return args.stream();
  }

  private static JsonNode corpus() throws Exception {
    try (InputStream in =
        new ClassPathResource("adversarial/injection-corpus.json").getInputStream()) {
      return new ObjectMapper().readTree(in);
    }
  }
}
