package com.careercopilot.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.shared.guardrail.GroundingException;
import com.careercopilot.shared.guardrail.GroundingValidator;
import com.careercopilot.shared.guardrail.JsonSchemaValidator;
import com.careercopilot.shared.guardrail.PiiRedactionAdvisor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;

/** LlmGateway unit tests: redaction, schema retry-then-throw, grounding, token logging. */
class LlmGatewayTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String VALID_RESUME =
      """
      {"candidateName":"Jane Doe",
       "experience":[{"sourceEntryId":1,"title":"Engineer","company":"Acme","bullets":["Did X"]}],
       "skills":["Java"]}""";

  private ChatClient chatClient;
  private TokenUsageLogRepository tokenUsageLogRepository;
  private LlmGateway gateway;

  @BeforeEach
  void setUp() {
    chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    when(builder.build()).thenReturn(chatClient);
    tokenUsageLogRepository = mock(TokenUsageLogRepository.class);
    LlmTaskConfig config = new LlmTaskConfig();
    Map<LlmTask, LlmTaskConfig.TierSettings> tiers = new EnumMap<>(LlmTask.class);
    LlmTaskConfig.TierSettings tier = new LlmTaskConfig.TierSettings();
    tier.setModel("test-model");
    tier.setMaxTokens(2500);
    tier.setTemperature(0.4);
    tiers.put(LlmTask.TAILORING, tier);
    config.setTiers(tiers);
    ObjectMapper objectMapper = new ObjectMapper();
    gateway =
        new LlmGateway(
            builder,
            config,
            new PiiRedactionAdvisor(new PiiRedactionService()),
            new JsonSchemaValidator(objectMapper),
            new GroundingValidator(),
            tokenUsageLogRepository);
  }

  private void stubResponses(String... texts) {
    ChatResponse[] responses = new ChatResponse[texts.length];
    for (int i = 0; i < texts.length; i++) {
      responses[i] = new ChatResponse(List.of(new Generation(new AssistantMessage(texts[i]))));
    }
    var stub =
        when(
            chatClient
                .prompt()
                .system(anyString())
                .user(anyString())
                .options(any(ChatOptions.class))
                .call()
                .chatResponse());
    if (responses.length == 1) {
      stub.thenReturn(responses[0]);
    } else {
      stub.thenReturn(responses[0], java.util.Arrays.copyOfRange(responses, 1, responses.length));
    }
  }

  @Test
  void callForJson_returnsValidatedPayload_andLogsTokens() {
    stubResponses(VALID_RESUME);

    LlmGateway.LlmJsonResult result =
        gateway.callForJson(
            USER_ID, LlmTask.TAILORING, "system", "user prompt", "resume-json-payload", null);

    assertThat(result.payload().get("candidateName").asText()).isEqualTo("Jane Doe");
    verify(tokenUsageLogRepository, times(1)).save(any(TokenUsageLog.class));
  }

  @Test
  void callForJson_redactsPiiBeforeSendingToProvider() {
    stubResponses(VALID_RESUME);

    gateway.callForJson(
        USER_ID,
        LlmTask.TAILORING,
        "system",
        "Candidate email is jane.doe@example.com and SSN 123-45-6789",
        "resume-json-payload",
        null);

    ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
    // Deep stubs return the same intermediate mock, so this re-invocation grabs the spec that
    // received .user(...) during the gateway call.
    var specAfterSystem = chatClient.prompt().system("probe");
    verify(specAfterSystem, atLeastOnce()).user(userPrompt.capture());
    // The last capture is the real gateway call (earlier ones come from stubbing invocations).
    assertThat(userPrompt.getValue())
        .doesNotContain("jane.doe@example.com")
        .doesNotContain("123-45-6789")
        .contains("[EMAIL]")
        .contains("[SSN]");
  }

  @Test
  void callForJson_retriesOnceOnSchemaFailure_thenSucceeds() {
    stubResponses("this is not JSON", VALID_RESUME);

    LlmGateway.LlmJsonResult result =
        gateway.callForJson(
            USER_ID, LlmTask.TAILORING, "system", "user", "resume-json-payload", null);

    assertThat(result.payload()).isNotNull();
    // Both attempts are individually token-logged.
    verify(tokenUsageLogRepository, times(2)).save(any(TokenUsageLog.class));
  }

  @Test
  void callForJson_throwsLlmOutputException_afterSecondSchemaFailure() {
    stubResponses("still not json", "{\"nope\":true}");

    assertThatThrownBy(
            () ->
                gateway.callForJson(
                    USER_ID, LlmTask.TAILORING, "system", "user", "resume-json-payload", null))
        .isInstanceOf(LlmOutputException.class);
    verify(tokenUsageLogRepository, times(2)).save(any(TokenUsageLog.class));
  }

  @Test
  void callForJson_enforcesGroundingWhenContextSupplied() {
    stubResponses(VALID_RESUME);
    // Valid schema but sourceEntryId 1 is not in the allowed set.
    GroundingValidator.GroundingContext context =
        new GroundingValidator.GroundingContext(
            Map.of(42L, new GroundingValidator.GroundingContext.Source("Other Co", "Role")));

    assertThatThrownBy(
            () ->
                gateway.callForJson(
                    USER_ID, LlmTask.TAILORING, "system", "user", "resume-json-payload", context))
        .isInstanceOf(GroundingException.class);
  }

  @Test
  void callForJson_wrapsProviderFailuresAsUnavailable() {
    when(chatClient
            .prompt()
            .system(anyString())
            .user(anyString())
            .options(any(ChatOptions.class))
            .call()
            .chatResponse())
        .thenThrow(new RuntimeException("connection refused"));

    assertThatThrownBy(
            () ->
                gateway.callForJson(
                    USER_ID, LlmTask.TAILORING, "system", "user", "resume-json-payload", null))
        .isInstanceOf(LlmUnavailableException.class);
  }
}
