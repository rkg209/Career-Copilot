package com.careercopilot.jobs.source;

import com.careercopilot.scoring.SkillAliasMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Deterministic, LLM-free skill extraction. Scans a posting's title + description as normalized
 * 1–3-grams, runs each gram through the scoring slice's {@link SkillAliasMap} (so "K8s" resolves to
 * "kubernetes"), and keeps it when the normalized form is present in the curated vocabulary ({@code
 * jobs/skill-vocabulary.yaml}). Reused across adapters that return no skills of their own
 * (Greenhouse, Lever, Ashby). No I/O, no cost.
 */
@Component
public class SkillExtractor {

  private static final String VOCAB_RESOURCE = "jobs/skill-vocabulary.yaml";
  private static final int MAX_NGRAM = 3;

  private final SkillAliasMap skillAliasMap;
  private final Set<String> vocabulary;

  public SkillExtractor(SkillAliasMap skillAliasMap) {
    this.skillAliasMap = skillAliasMap;
    this.vocabulary = loadVocabulary(skillAliasMap);
  }

  @SuppressWarnings("unchecked")
  private static Set<String> loadVocabulary(SkillAliasMap aliasMap) {
    try (InputStream in = new ClassPathResource(VOCAB_RESOURCE).getInputStream()) {
      Map<String, Object> raw = new Yaml().load(in);
      Object skills = raw == null ? null : raw.get("skills");
      Set<String> vocab = new LinkedHashSet<>();
      if (skills instanceof List<?> list) {
        for (Object entry : list) {
          String normalized = aliasMap.normalize(String.valueOf(entry));
          if (!normalized.isEmpty()) {
            vocab.add(normalized);
          }
        }
      }
      return vocab;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load skill vocabulary from " + VOCAB_RESOURCE, e);
    }
  }

  /**
   * Extracts a de-duplicated, canonical list of skills mentioned in the title and full text. Order
   * follows first appearance. Returns an empty list for null/blank input.
   */
  public List<String> extract(String title, String fullText) {
    Set<String> found = new LinkedHashSet<>();
    scan(title, found);
    scan(fullText, found);
    return new ArrayList<>(found);
  }

  private void scan(String text, Set<String> found) {
    if (text == null || text.isBlank()) {
      return;
    }
    String[] tokens = tokenize(text);
    for (int start = 0; start < tokens.length; start++) {
      StringBuilder gram = new StringBuilder();
      for (int len = 1; len <= MAX_NGRAM && start + len <= tokens.length; len++) {
        if (len > 1) {
          gram.append(' ');
        }
        gram.append(tokens[start + len - 1]);
        String normalized = skillAliasMap.normalize(gram.toString());
        if (vocabulary.contains(normalized)) {
          found.add(normalized);
        }
      }
    }
  }

  /**
   * Lowercases and collapses everything except letters, digits, '+' and '#' to single spaces —
   * mirroring the base normalization {@link SkillAliasMap#normalize} applies, so token grams line
   * up with the alias table. Kept local to avoid widening the scoring slice's API.
   */
  private static String[] tokenize(String text) {
    return text.toLowerCase().replaceAll("[^a-z0-9+#]+", " ").trim().split(" ");
  }
}
