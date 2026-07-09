package com.careercopilot.shared.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Grounded-only guardrail (CLAUDE.md): every experience item in tailored output must reference a
 * retrieved corpus entry via {@code sourceEntryId}, and the employer must match that entry — the
 * LLM is never allowed to invent experience.
 */
@Component
public class GroundingValidator {

  private static final Logger log = LoggerFactory.getLogger(GroundingValidator.class);

  /** What the LLM was allowed to draw from: retrieved entry id → its immutable facts. */
  public record GroundingContext(Map<Long, Source> allowedSources) {
    public record Source(String company, String role) {}
  }

  /**
   * Throws {@link GroundingException} when any {@code experience[]}, {@code projects[]}, or {@code
   * positionsOfResponsibility[]} item references an unknown {@code sourceEntryId}, or when an
   * experience item contradicts the source entry's employer.
   */
  public void validate(JsonNode payload, GroundingContext context) {
    List<String> violations = new ArrayList<>();
    JsonNode experience = payload.path("experience");
    if (experience.isArray()) {
      for (JsonNode item : experience) {
        GroundingContext.Source source = knownSource(item, "experience", context, violations);
        if (source == null) {
          continue;
        }
        String company = item.path("company").asText("");
        if (!company.equalsIgnoreCase(source.company())) {
          violations.add(
              "sourceEntryId "
                  + item.path("sourceEntryId").asLong(-1)
                  + " company mismatch: \""
                  + company
                  + "\"");
        }
        String title = item.path("title").asText("");
        if (!title.equalsIgnoreCase(source.role())) {
          // Titles may be legitimately rephrased; log for observability, don't reject.
          log.debug(
              "Grounding note: title \"{}\" differs from source role \"{}\"", title, source.role());
        }
      }
    }
    for (String section : List.of("projects", "positionsOfResponsibility")) {
      JsonNode items = payload.path(section);
      if (items.isArray()) {
        for (JsonNode item : items) {
          knownSource(item, section, context, violations);
        }
      }
    }
    if (!violations.isEmpty()) {
      log.warn("Grounding validation failed: {} violation(s)", violations.size());
      throw new GroundingException(violations);
    }
  }

  private static GroundingContext.Source knownSource(
      JsonNode item, String section, GroundingContext context, List<String> violations) {
    long sourceEntryId = item.path("sourceEntryId").asLong(-1);
    GroundingContext.Source source = context.allowedSources().get(sourceEntryId);
    if (source == null) {
      violations.add("Unknown sourceEntryId " + sourceEntryId + " in " + section);
    }
    return source;
  }
}
