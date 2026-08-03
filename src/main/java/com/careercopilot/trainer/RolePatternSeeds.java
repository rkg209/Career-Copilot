package com.careercopilot.trainer;

import com.careercopilot.tracker.InterviewRoundType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Curated, company-agnostic seed questions (trainer/role-patterns.yaml), keyed role category ->
 * round type -> questions. Loaded once like {@code SkillAliasMap} loads its alias table.
 */
@Component
public class RolePatternSeeds {

  private static final String RESOURCE = "trainer/role-patterns.yaml";

  /** role category (case-insensitive) -> round type name -> seed questions. */
  private final Map<String, Map<String, List<String>>> seeds;

  public RolePatternSeeds() {
    this.seeds = load();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, List<String>>> load() {
    try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
      Map<String, Object> raw = new Yaml().load(in);
      Map<String, Map<String, List<String>>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      if (raw != null) {
        raw.forEach((role, byRound) -> result.put(role, (Map<String, List<String>>) byRound));
      }
      return result;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load role-pattern seeds from " + RESOURCE, e);
    }
  }

  /**
   * Seed questions for a role category, optionally narrowed to one round type. Falls back to an
   * empty list when the role or round type has no curated seeds.
   */
  public List<String> seedsFor(String roleCategory, InterviewRoundType roundType) {
    if (roleCategory == null) {
      return List.of();
    }
    Map<String, List<String>> byRound = seeds.get(roleCategory);
    if (byRound == null) {
      return List.of();
    }
    if (roundType != null) {
      return byRound.getOrDefault(roundType.name(), List.of());
    }
    return byRound.values().stream().flatMap(List::stream).toList();
  }
}
