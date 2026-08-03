package com.careercopilot.shared;

import io.micrometer.core.annotation.Timed;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

/**
 * Single entry point for every embedding call, mirroring the LlmGateway discipline: PII is redacted
 * before the provider sees the text, and every call is logged to token_usage_log.
 */
@Component
public class EmbeddingGateway {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingGateway.class);

  private final EmbeddingModel embeddingModel;
  private final PiiRedactionService piiRedactionService;
  private final TokenUsageLogRepository tokenUsageLogRepository;

  public EmbeddingGateway(
      EmbeddingModel embeddingModel,
      PiiRedactionService piiRedactionService,
      TokenUsageLogRepository tokenUsageLogRepository) {
    this.embeddingModel = embeddingModel;
    this.piiRedactionService = piiRedactionService;
    this.tokenUsageLogRepository = tokenUsageLogRepository;
  }

  /**
   * Embeds the given text (after PII redaction). Throws {@link EmbeddingUnavailableException} when
   * the provider is unreachable — callers leave the entry PENDING and retry later.
   */
  @Timed(
      value = "embedding.embed",
      percentiles = {0.5, 0.99})
  public float[] embed(UUID userId, LlmTask task, String text) {
    String redacted = piiRedactionService.redact(text);
    long start = System.currentTimeMillis();
    EmbeddingResponse response;
    try {
      response = embeddingModel.embedForResponse(List.of(redacted));
    } catch (RuntimeException e) {
      throw new EmbeddingUnavailableException("Embedding provider call failed", e);
    }
    int latencyMs = (int) (System.currentTimeMillis() - start);

    String modelName = resolveModelName(response);
    int promptTokens = resolvePromptTokens(response);
    tokenUsageLogRepository.save(
        new TokenUsageLog(userId, task, modelName, promptTokens, 0, latencyMs));
    log.debug(
        "task={} inputTokens={} outputTokens=0 modelUsed={} latencyMs={}",
        task,
        promptTokens,
        modelName,
        latencyMs);
    return response.getResults().get(0).getOutput();
  }

  private static String resolveModelName(EmbeddingResponse response) {
    if (response.getMetadata() != null && response.getMetadata().getModel() != null) {
      return response.getMetadata().getModel();
    }
    return "unknown";
  }

  private static int resolvePromptTokens(EmbeddingResponse response) {
    if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
      Integer prompt = response.getMetadata().getUsage().getPromptTokens();
      return prompt != null ? prompt : 0;
    }
    return 0;
  }
}
