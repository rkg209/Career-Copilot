package com.careercopilot.intel;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.shared.PiiRedactionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

/**
 * Hardening corpus (NFR-8.5): a batch of PII-laden interview-log text that {@link PiiScrubber} must
 * anonymize with zero residual leakage, plus a control corpus of technical bigrams that must
 * survive untouched. Detection here deliberately uses an independent regex set from production —
 * testing with the same patterns that did the redaction proves nothing about whether it actually
 * worked.
 */
class PiiLeakCorpusTest {

  // Independent detectors — intentionally simpler/broader than the production patterns.
  private static final Pattern EMAIL_DETECTOR =
      Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
  private static final Pattern PHONE_DETECTOR = Pattern.compile("\\d(?:[\\s.\\-()]?\\d){6,}");
  private static final Pattern SSN_DETECTOR = Pattern.compile("\\d{3}-\\d{2}-\\d{4}");
  private static final Pattern URL_DETECTOR =
      Pattern.compile(
          "https?://\\S+|\\b(?:[a-z0-9-]+\\.)+[a-z]{2,}(?:/\\S*)?", Pattern.CASE_INSENSITIVE);
  private static final Pattern NAME_DETECTOR =
      Pattern.compile("\\b[A-Z][a-z]{1,}\\s+[A-Z][a-z]{1,}\\b");

  private final PiiScrubber scrubber = new PiiScrubber(new PiiRedactionService());

  @ParameterizedTest(name = "{0}")
  @MethodSource("logs")
  void scrub_leavesNoResidualPii(String name, String text) {
    String scrubbed = scrubber.scrub(text);

    assertThat(EMAIL_DETECTOR.matcher(scrubbed).find()).as("email leak in '%s'", name).isFalse();
    assertThat(PHONE_DETECTOR.matcher(scrubbed).find()).as("phone leak in '%s'", name).isFalse();
    assertThat(SSN_DETECTOR.matcher(scrubbed).find()).as("SSN leak in '%s'", name).isFalse();
    assertThat(URL_DETECTOR.matcher(scrubbed).find()).as("URL leak in '%s'", name).isFalse();
    assertThat(NAME_DETECTOR.matcher(scrubbed).find()).as("name leak in '%s'", name).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("controls")
  void scrub_preservesTechnicalBigrams(String text) {
    assertThat(scrubber.scrub(text)).isEqualTo(text);
  }

  static Stream<Arguments> logs() throws Exception {
    JsonNode root = corpus();
    List<Arguments> args = new ArrayList<>();
    for (JsonNode log : root.get("logs")) {
      args.add(Arguments.of(log.get("name").asText(), log.get("text").asText()));
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
    try (InputStream in = new ClassPathResource("adversarial/pii-corpus.json").getInputStream()) {
      return new ObjectMapper().readTree(in);
    }
  }
}
