package com.careercopilot.shared;

import com.careercopilot.shared.guardrail.GroundingValidator;
import com.careercopilot.shared.guardrail.JsonSchemaValidator;
import com.careercopilot.shared.guardrail.PiiRedactionAdvisor;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

/**
 * Single entry point for every chat LLM call (CLAUDE.md LLM rules). Model/tier selection is
 * config-driven via {@link LlmTaskConfig}; PII is redacted before the provider sees any text;
 * output is schema-validated (retry once, then {@link LlmOutputException}); grounding is enforced
 * when a {@link GroundingValidator.GroundingContext} is supplied; every call is logged to
 * token_usage_log. Callers never touch the provider SDK directly.
 *
 * <p>Prompt-injection sanitization of untrusted text happens where the prompt is assembled (see
 * PromptInjectionAdvisor.fence) — by the time text reaches this gateway it must already be fenced.
 */
@Component
public class LlmGateway {

  private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

  private final ChatClient chatClient;
  private final LlmTaskConfig llmTaskConfig;
  private final PiiRedactionAdvisor piiRedactionAdvisor;
  private final JsonSchemaValidator jsonSchemaValidator;
  private final GroundingValidator groundingValidator;
  private final TokenUsageLogRepository tokenUsageLogRepository;

  public LlmGateway(
      ChatClient.Builder chatClientBuilder,
      LlmTaskConfig llmTaskConfig,
      PiiRedactionAdvisor piiRedactionAdvisor,
      JsonSchemaValidator jsonSchemaValidator,
      GroundingValidator groundingValidator,
      TokenUsageLogRepository tokenUsageLogRepository) {
    this.chatClient = chatClientBuilder.build();
    this.llmTaskConfig = llmTaskConfig;
    this.piiRedactionAdvisor = piiRedactionAdvisor;
    this.jsonSchemaValidator = jsonSchemaValidator;
    this.groundingValidator = groundingValidator;
    this.tokenUsageLogRepository = tokenUsageLogRepository;
  }

  /** Validated JSON output plus the call metadata callers persist alongside it. */
  public record LlmJsonResult(JsonNode payload, String modelUsed, int totalTokens) {}

  /**
   * Calls the tier configured for {@code task} and returns schema-valid JSON. Retries once on
   * schema failure (feeding the validation errors back), then throws {@link LlmOutputException}.
   * Provider failures throw {@link LlmUnavailableException}.
   */
  public LlmJsonResult callForJson(
      UUID userId,
      LlmTask task,
      String systemPrompt,
      String userPrompt,
      String schemaName,
      GroundingValidator.GroundingContext groundingContext) {
    LlmTaskConfig.TierSettings tier = llmTaskConfig.require(task);
    String redactedSystem = piiRedactionAdvisor.redact(systemPrompt);
    String redactedUser = piiRedactionAdvisor.redact(userPrompt);

    String modelUsed = tier.getModel();
    int totalTokens = 0;
    List<String> lastErrors = List.of();
    String attemptUserPrompt = redactedUser;

    for (int attempt = 1; attempt <= 2; attempt++) {
      ChatResponse response = call(tier, redactedSystem, attemptUserPrompt, userId, task);
      totalTokens += usedTokens(response);
      if (response.getMetadata() != null && response.getMetadata().getModel() != null) {
        modelUsed = response.getMetadata().getModel();
      }
      String rawText = response.getResult().getOutput().getText();
      JsonSchemaValidator.ValidationResult result =
          jsonSchemaValidator.validate(rawText, schemaName);
      if (result.valid()) {
        if (groundingContext != null) {
          groundingValidator.validate(result.payload(), groundingContext);
        }
        return new LlmJsonResult(result.payload(), modelUsed, totalTokens);
      }
      lastErrors = result.errors();
      log.warn("task={} attempt={} schema validation failed: {}", task, attempt, lastErrors);
      attemptUserPrompt =
          redactedUser
              + "\n\nYour previous response was rejected because it did not match the required"
              + " JSON schema. Errors: "
              + String.join("; ", lastErrors)
              + "\nReturn ONLY corrected JSON conforming to the schema.";
    }
    throw new LlmOutputException(task, lastErrors);
  }

  private ChatResponse call(
      LlmTaskConfig.TierSettings tier,
      String systemPrompt,
      String userPrompt,
      UUID userId,
      LlmTask task) {
    long start = System.currentTimeMillis();
    ChatResponse response;
    try {
      response =
          chatClient
              .prompt()
              .system(systemPrompt)
              .user(userPrompt)
              .options(
                  ChatOptions.builder()
                      .model(tier.getModel())
                      .maxTokens(tier.getMaxTokens())
                      .temperature(tier.getTemperature())
                      .build())
              .call()
              .chatResponse();
    } catch (RuntimeException e) {
      throw new LlmUnavailableException("Chat LLM provider call failed", e);
    }
    if (response == null || response.getResult() == null) {
      throw new LlmUnavailableException("Chat LLM provider returned an empty response", null);
    }
    int latencyMs = (int) (System.currentTimeMillis() - start);
    String modelName =
        response.getMetadata() != null && response.getMetadata().getModel() != null
            ? response.getMetadata().getModel()
            : tier.getModel();
    int promptTokens = 0;
    int completionTokens = 0;
    if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
      Usage usage = response.getMetadata().getUsage();
      promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
      completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
    }
    tokenUsageLogRepository.save(
        new TokenUsageLog(userId, task, modelName, promptTokens, completionTokens, latencyMs));
    log.debug(
        "task={} inputTokens={} outputTokens={} modelUsed={} latencyMs={}",
        task,
        promptTokens,
        completionTokens,
        modelName,
        latencyMs);
    return response;
  }

  private static int usedTokens(ChatResponse response) {
    if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
      Integer total = response.getMetadata().getUsage().getTotalTokens();
      return total != null ? total : 0;
    }
    return 0;
  }
}
