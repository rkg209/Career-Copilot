package com.careercopilot.scoring;

import com.careercopilot.shared.LlmGateway;
import com.careercopilot.shared.LlmTask;
import com.careercopilot.shared.guardrail.PromptInjectionAdvisor;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * LLM keyword extraction (EXTRACTION tier): classifies JD terms into required vs preferred, then
 * normalizes them deterministically through {@link SkillAliasMap}. The LLM only classifies terms —
 * it never assigns any score.
 */
@Component
public class KeywordExtractor {

  private final LlmGateway llmGateway;
  private final PromptInjectionAdvisor promptInjectionAdvisor;
  private final SkillAliasMap skillAliasMap;

  public KeywordExtractor(
      LlmGateway llmGateway,
      PromptInjectionAdvisor promptInjectionAdvisor,
      SkillAliasMap skillAliasMap) {
    this.llmGateway = llmGateway;
    this.promptInjectionAdvisor = promptInjectionAdvisor;
    this.skillAliasMap = skillAliasMap;
  }

  /**
   * Keywords keyed by normalized form, valued by the display form the JD used. A term listed as
   * both required and preferred counts as required only.
   */
  public record ExtractedKeywords(
      Map<String, String> required, Map<String, String> preferred, String modelUsed) {}

  public ExtractedKeywords extract(UUID userId, String jobDescription) {
    String fencedJd = promptInjectionAdvisor.fence(jobDescription);
    LlmGateway.LlmJsonResult result =
        llmGateway.callForJson(
            userId,
            LlmTask.EXTRACTION,
            ScoringPrompts.KEYWORD_EXTRACTION_SYSTEM_PROMPT,
            ScoringPrompts.keywordExtractionUserPrompt(fencedJd),
            "jd-keyword-extraction",
            null);

    Map<String, String> required = normalizedKeywords(result.payload().get("required"));
    Map<String, String> preferred = normalizedKeywords(result.payload().get("preferred"));
    preferred.keySet().removeAll(required.keySet());
    return new ExtractedKeywords(required, preferred, result.modelUsed());
  }

  private Map<String, String> normalizedKeywords(JsonNode terms) {
    Map<String, String> keywords = new LinkedHashMap<>();
    if (terms != null && terms.isArray()) {
      for (JsonNode term : terms) {
        String display = term.asText().trim();
        String normalized = skillAliasMap.normalize(display);
        if (!normalized.isEmpty()) {
          keywords.putIfAbsent(normalized, display);
        }
      }
    }
    return keywords;
  }
}
