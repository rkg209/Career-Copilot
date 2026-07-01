package com.careercopilot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmbeddingPiiRedactionTest {

  private final PiiRedactionService service = new PiiRedactionService();

  @Test
  void redact_stripsEmails() {
    assertThat(service.redact("Reach me at jane.smith+work@example.co.uk today"))
        .isEqualTo("Reach me at [EMAIL] today");
  }

  @Test
  void redact_stripsPhoneNumbers() {
    assertThat(service.redact("Call 555-123-4567 or +1 (415) 555-0199"))
        .doesNotContain("555-123-4567")
        .doesNotContain("555-0199");
  }

  @Test
  void redact_stripsSsn() {
    assertThat(service.redact("SSN 123-45-6789 on file")).isEqualTo("SSN [SSN] on file");
  }

  @Test
  void redact_leavesNormalTextUntouched() {
    String text = "Led a team of 5 engineers migrating to Postgres 16";
    assertThat(service.redact(text)).isEqualTo(text);
  }

  @Test
  void redact_handlesNullAndBlank() {
    assertThat(service.redact(null)).isNull();
    assertThat(service.redact("  ")).isEqualTo("  ");
  }
}
