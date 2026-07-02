package com.careercopilot.shared.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatorTest {

  private final JsonSchemaValidator validator = new JsonSchemaValidator(new ObjectMapper());

  private static final String VALID_RESUME =
      """
      {"candidateName":"Jane Doe",
       "experience":[{"sourceEntryId":1,"title":"Engineer","company":"Acme","bullets":["Did X"]}],
       "skills":["Java"]}""";

  @Test
  void validate_acceptsSchemaConformantResume() {
    var result = validator.validate(VALID_RESUME, "resume-json-payload");
    assertThat(result.valid()).isTrue();
    assertThat(result.payload().get("candidateName").asText()).isEqualTo("Jane Doe");
  }

  @Test
  void validate_stripsMarkdownCodeFences() {
    var result = validator.validate("```json\n" + VALID_RESUME + "\n```", "resume-json-payload");
    assertThat(result.valid()).isTrue();
  }

  @Test
  void validate_rejectsMissingRequiredFields() {
    var result = validator.validate("{\"candidateName\":\"Jane\"}", "resume-json-payload");
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).isNotEmpty();
  }

  @Test
  void validate_rejectsNonJson() {
    var result = validator.validate("Sure! Here is your resume:", "resume-json-payload");
    assertThat(result.valid()).isFalse();
  }

  @Test
  void validate_acceptsCoverLetterPayload() {
    String coverLetter =
        """
        {"candidateName":"Jane Doe","salutation":"Dear Hiring Manager,",
         "bodyParagraphs":["I built things."],"closing":"Sincerely,"}""";
    assertThat(validator.validate(coverLetter, "cover-letter-json-payload").valid()).isTrue();
  }

  @Test
  void validate_rejectsExperienceWithoutSourceEntryId() {
    String payload =
        """
        {"candidateName":"Jane",
         "experience":[{"title":"Engineer","company":"Acme","bullets":["x"]}],
         "skills":["Java"]}""";
    var result = validator.validate(payload, "resume-json-payload");
    assertThat(result.valid()).isFalse();
  }
}
