package com.careercopilot.intel;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.shared.PiiRedactionService;
import org.junit.jupiter.api.Test;

class PiiScrubberTest {

  private final PiiScrubber scrubber = new PiiScrubber(new PiiRedactionService());

  @Test
  void scrub_email_isRedacted() {
    String result = scrubber.scrub("Reach the recruiter at jane.doe@acme.com for feedback.");
    assertThat(result).doesNotContain("jane.doe@acme.com").contains("[EMAIL]");
  }

  @Test
  void scrub_phoneNumber_isRedacted() {
    String result = scrubber.scrub("Call me back at 415-555-0134 after the onsite.");
    assertThat(result).doesNotContain("415-555-0134").contains("[PHONE]");
  }

  @Test
  void scrub_url_isRedacted() {
    String result = scrubber.scrub("See https://example.com/portfolio/jane for my work.");
    assertThat(result).doesNotContain("https://example.com/portfolio/jane").contains("[URL]");
  }

  @Test
  void scrub_likelyPersonName_isRedacted() {
    String result = scrubber.scrub("John Smith ran the technical interview.");
    assertThat(result).doesNotContain("John Smith").contains("[PERSON]");
  }

  @Test
  void scrub_knownTechnicalBigram_isPreserved() {
    String result = scrubber.scrub("We discussed System Design and Dynamic Programming.");
    assertThat(result).contains("System Design").contains("Dynamic Programming");
  }

  @Test
  void scrub_longText_isTruncatedTo2000Chars() {
    String longText = "a".repeat(3000);
    String result = scrubber.scrub(longText);
    assertThat(result).hasSize(2000);
  }

  @Test
  void scrub_blankOrNull_returnsUnchanged() {
    assertThat(scrubber.scrub(null)).isNull();
    assertThat(scrubber.scrub("")).isEmpty();
  }
}
