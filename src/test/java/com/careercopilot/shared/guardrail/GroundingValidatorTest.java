package com.careercopilot.shared.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GroundingValidatorTest {

  private final GroundingValidator validator = new GroundingValidator();
  private final ObjectMapper mapper = new ObjectMapper();

  private final GroundingValidator.GroundingContext context =
      new GroundingValidator.GroundingContext(
          Map.of(
              7L, new GroundingValidator.GroundingContext.Source("Acme Corp", "Backend Engineer"),
              9L, new GroundingValidator.GroundingContext.Source("Globex", "Data Engineer")));

  @Test
  void validate_passesWhenAllEntriesAreGrounded() throws Exception {
    JsonNode payload =
        mapper.readTree(
            """
            {"experience":[
              {"sourceEntryId":7,"title":"Backend Engineer","company":"Acme Corp","bullets":["x"]},
              {"sourceEntryId":9,"title":"Data Engineer","company":"globex","bullets":["y"]}
            ]}""");
    assertThatCode(() -> validator.validate(payload, context)).doesNotThrowAnyException();
  }

  @Test
  void validate_rejectsUnknownSourceEntryId() throws Exception {
    JsonNode payload =
        mapper.readTree(
            """
            {"experience":[{"sourceEntryId":999,"title":"CEO","company":"Invented Inc","bullets":["x"]}]}""");
    assertThatThrownBy(() -> validator.validate(payload, context))
        .isInstanceOf(GroundingException.class)
        .satisfies(
            e ->
                assertThat(((GroundingException) e).getViolations())
                    .anyMatch(v -> v.contains("999")));
  }

  @Test
  void validate_rejectsCompanyMismatch() throws Exception {
    JsonNode payload =
        mapper.readTree(
            """
            {"experience":[{"sourceEntryId":7,"title":"Backend Engineer","company":"Different Corp","bullets":["x"]}]}""");
    assertThatThrownBy(() -> validator.validate(payload, context))
        .isInstanceOf(GroundingException.class);
  }

  @Test
  void validate_ignoresPayloadsWithoutExperienceArray() throws Exception {
    JsonNode payload = mapper.readTree("{\"salutation\":\"Dear team\"}");
    assertThatCode(() -> validator.validate(payload, context)).doesNotThrowAnyException();
  }

  @Test
  void validate_acceptsGroundedProjectsAndPositions() throws Exception {
    JsonNode payload =
        mapper.readTree(
            """
            {"projects":[{"sourceEntryId":7,"name":"Quadcopter control","bullets":["x"]}],
             "positionsOfResponsibility":[{"sourceEntryId":9,"name":"Team Lead","bullets":["y"]}]}""");
    assertThatCode(() -> validator.validate(payload, context)).doesNotThrowAnyException();
  }

  @Test
  void validate_rejectsUnknownSourceEntryIdInProjects() throws Exception {
    JsonNode payload =
        mapper.readTree(
            """
            {"projects":[{"sourceEntryId":555,"name":"Invented Project","bullets":["x"]}]}""");
    assertThatThrownBy(() -> validator.validate(payload, context))
        .isInstanceOf(GroundingException.class)
        .satisfies(
            e ->
                assertThat(((GroundingException) e).getViolations())
                    .anyMatch(v -> v.contains("555") && v.contains("projects")));
  }

  @Test
  void validate_rejectsUnknownSourceEntryIdInPositions() throws Exception {
    JsonNode payload =
        mapper.readTree(
            """
            {"positionsOfResponsibility":[{"sourceEntryId":666,"name":"Fake Lead","bullets":["x"]}]}""");
    assertThatThrownBy(() -> validator.validate(payload, context))
        .isInstanceOf(GroundingException.class);
  }
}
