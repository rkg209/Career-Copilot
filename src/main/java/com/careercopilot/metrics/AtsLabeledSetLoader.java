package com.careercopilot.metrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads and caches the 20-JD hand-labeled eval set at startup (also the source for {@code
 * AtsEvalHarnessTest}, which reads the same {@code eval/ats-labeled-set.json} resource directly).
 */
@Component
public class AtsLabeledSetLoader {

  private static final String RESOURCE_PATH = "eval/ats-labeled-set.json";

  private final List<LabeledEntry> entries;

  public AtsLabeledSetLoader(ObjectMapper objectMapper) {
    try (InputStream in = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
      this.entries = objectMapper.readValue(in, new TypeReference<List<LabeledEntry>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load " + RESOURCE_PATH, e);
    }
  }

  public List<LabeledEntry> entries() {
    return entries;
  }

  public record LabeledEntry(
      String id,
      String title,
      List<String> required,
      List<String> preferred,
      List<String> resumeText,
      List<String> baselineResumeText,
      double labeledScore) {}
}
