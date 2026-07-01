package com.careercopilot.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

class EmbeddingGatewayTest {

  private EmbeddingModel embeddingModel;
  private TokenUsageLogRepository tokenUsageLogRepository;
  private EmbeddingGateway gateway;

  @BeforeEach
  void setUp() {
    embeddingModel = mock(EmbeddingModel.class);
    tokenUsageLogRepository = mock(TokenUsageLogRepository.class);
    gateway =
        new EmbeddingGateway(embeddingModel, new PiiRedactionService(), tokenUsageLogRepository);
  }

  @Test
  @SuppressWarnings("unchecked")
  void embed_stripsPiiBeforeProviderCall() {
    when(embeddingModel.embedForResponse(anyList())).thenReturn(response());

    gateway.embed(
        UUID.randomUUID(), LlmTask.EMBEDDING, "Contact john.doe@acme.com or 555-123-4567");

    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(embeddingModel).embedForResponse(captor.capture());
    String sent = captor.getValue().get(0);
    assertThat(sent).doesNotContain("john.doe@acme.com").doesNotContain("555-123-4567");
    assertThat(sent).contains("[EMAIL]").contains("[PHONE]");
  }

  @Test
  void embed_logsUsageWithEmbeddingTask() {
    when(embeddingModel.embedForResponse(anyList())).thenReturn(response());
    UUID userId = UUID.randomUUID();

    float[] vector = gateway.embed(userId, LlmTask.EMBEDDING, "Backend engineer at Acme");

    assertThat(vector).hasSize(3);
    ArgumentCaptor<TokenUsageLog> captor = ArgumentCaptor.forClass(TokenUsageLog.class);
    verify(tokenUsageLogRepository).save(captor.capture());
    TokenUsageLog logged = captor.getValue();
    assertThat(logged.getUserId()).isEqualTo(userId);
    assertThat(logged.getTask()).isEqualTo(LlmTask.EMBEDDING);
    assertThat(logged.getModelName()).isEqualTo("nomic-embed-text");
    assertThat(logged.getPromptTokens()).isEqualTo(7);
    assertThat(logged.getCompletionTokens()).isZero();
  }

  @Test
  void embed_providerFailure_throwsEmbeddingUnavailable() {
    when(embeddingModel.embedForResponse(anyList()))
        .thenThrow(new RuntimeException("connection refused"));

    assertThatThrownBy(() -> gateway.embed(UUID.randomUUID(), LlmTask.EMBEDDING, "text"))
        .isInstanceOf(EmbeddingUnavailableException.class);
  }

  private static EmbeddingResponse response() {
    return new EmbeddingResponse(
        List.of(new Embedding(new float[] {0.1f, 0.2f, 0.3f}, 0)),
        new EmbeddingResponseMetadata("nomic-embed-text", new DefaultUsage(7, 0)));
  }
}
