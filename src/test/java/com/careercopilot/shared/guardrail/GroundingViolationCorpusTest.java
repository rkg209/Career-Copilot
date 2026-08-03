package com.careercopilot.shared.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

/**
 * Hardening corpus (NFR-8.5): every payload here must be rejected by {@link GroundingValidator}
 * with a violation naming the offending entry — invented experience must never slip through.
 */
class GroundingViolationCorpusTest {

  private final GroundingValidator validator = new GroundingValidator();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final GroundingValidator.GroundingContext context =
      new GroundingValidator.GroundingContext(
          Map.of(
              7L, new GroundingValidator.GroundingContext.Source("Acme Corp", "Backend Engineer"),
              9L, new GroundingValidator.GroundingContext.Source("Globex", "Data Engineer")));

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void validate_rejectsInventedOrUngroundedContent(
      String name, JsonNode payload, List<String> expectSubstrings) {
    assertThatThrownBy(() -> validator.validate(payload, context))
        .as("case '%s' must be rejected", name)
        .isInstanceOf(GroundingException.class)
        .satisfies(
            e -> {
              List<String> violations = ((GroundingException) e).getViolations();
              assertThat(violations).as("case '%s' violations", name).isNotEmpty();
              for (String expected : expectSubstrings) {
                assertThat(violations)
                    .as("case '%s' should name '%s'", name, expected)
                    .anyMatch(v -> v.contains(expected));
              }
            });
  }

  static Stream<Arguments> cases() throws Exception {
    JsonNode root;
    try (InputStream in =
        new ClassPathResource("adversarial/grounding-violation-corpus.json").getInputStream()) {
      root = new ObjectMapper().readTree(in);
    }
    List<Arguments> args = new ArrayList<>();
    for (JsonNode entry : root) {
      List<String> expect = new ArrayList<>();
      entry.get("expectSubstrings").forEach(s -> expect.add(s.asText()));
      args.add(Arguments.of(entry.get("name").asText(), entry.get("payload"), expect));
    }
    return args.stream();
  }
}
