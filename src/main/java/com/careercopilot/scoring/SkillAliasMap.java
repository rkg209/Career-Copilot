package com.careercopilot.scoring;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Deterministic keyword normalization: lowercase, collapse punctuation to single spaces, then map
 * through the curated alias table (scoring/skill-aliases.yaml). Both JD keywords and resume terms
 * go through the same normalization, so "K8s" in a JD matches "Kubernetes" on a resume.
 */
@Component
public class SkillAliasMap {

  private static final String RESOURCE = "scoring/skill-aliases.yaml";

  private final Map<String, String> aliases;

  public SkillAliasMap() {
    this.aliases = loadAliases();
  }

  private static Map<String, String> loadAliases() {
    try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
      Map<String, Object> raw = new Yaml().load(in);
      return raw.entrySet().stream()
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  e -> baseNormalize(e.getKey()), e -> baseNormalize(String.valueOf(e.getValue()))));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load skill alias map from " + RESOURCE, e);
    }
  }

  /** Lowercases and collapses everything except letters, digits, '+' and '#' to single spaces. */
  static String baseNormalize(String term) {
    if (term == null) {
      return "";
    }
    return term.toLowerCase().replaceAll("[^a-z0-9+#]+", " ").trim();
  }

  /** Full normalization: base normalize, then whole-phrase alias lookup. */
  public String normalize(String term) {
    String base = baseNormalize(term);
    return aliases.getOrDefault(base, base);
  }

  /** Normalizes every term, dropping blanks and duplicates while preserving order. */
  public Set<String> normalizeAll(Collection<String> terms) {
    Set<String> normalized = new LinkedHashSet<>();
    for (String term : terms) {
      String n = normalize(term);
      if (!n.isEmpty()) {
        normalized.add(n);
      }
    }
    return normalized;
  }
}
