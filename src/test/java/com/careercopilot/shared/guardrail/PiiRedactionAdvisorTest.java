package com.careercopilot.shared.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.shared.PiiRedactionService;
import org.junit.jupiter.api.Test;

class PiiRedactionAdvisorTest {

  private final PiiRedactionAdvisor advisor = new PiiRedactionAdvisor(new PiiRedactionService());

  @Test
  void redact_stripsEmailFromText() {
    String redacted = advisor.redact("Contact jane.doe@acme.com for a reference.");

    assertThat(redacted).doesNotContain("jane.doe@acme.com").contains("[EMAIL]");
  }

  @Test
  void redact_stripsPhoneFromText() {
    String redacted = advisor.redact("Call back at 415-555-0134 today.");

    assertThat(redacted).doesNotContain("415-555-0134").contains("[PHONE]");
  }

  @Test
  void redact_stripsSsnFromText() {
    String redacted = advisor.redact("SSN on file: 123-45-6789.");

    assertThat(redacted).doesNotContain("123-45-6789").contains("[SSN]");
  }

  @Test
  void redact_leavesCleanTextUntouched() {
    String text = "We need a Java engineer with Spring Boot and PostgreSQL experience.";

    assertThat(advisor.redact(text)).isEqualTo(text);
  }

  @Test
  void redact_delegatesToPiiRedactionService_forBothPromptAndResponseText() {
    String prompt = "Tailor this resume for jane.doe@acme.com, phone 415-555-0134.";

    String redacted = advisor.redact(prompt);

    assertThat(redacted).contains("[EMAIL]").contains("[PHONE]");
  }
}
