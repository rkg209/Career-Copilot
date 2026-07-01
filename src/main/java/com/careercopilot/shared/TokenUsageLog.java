package com.careercopilot.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "token_usage_log")
public class TokenUsageLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private UUID userId;

  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false, columnDefinition = "llm_task")
  private LlmTask task;

  @Column(name = "model_name", nullable = false)
  private String modelName;

  @Column(name = "prompt_tokens", nullable = false)
  private int promptTokens;

  @Column(name = "completion_tokens", nullable = false)
  private int completionTokens;

  @Column(name = "total_tokens", insertable = false, updatable = false)
  private Integer totalTokens;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @CreationTimestamp
  @Column(name = "called_at", nullable = false, updatable = false)
  private Instant calledAt;

  protected TokenUsageLog() {}

  public TokenUsageLog(
      UUID userId,
      LlmTask task,
      String modelName,
      int promptTokens,
      int completionTokens,
      Integer latencyMs) {
    this.userId = userId;
    this.task = task;
    this.modelName = modelName;
    this.promptTokens = promptTokens;
    this.completionTokens = completionTokens;
    this.latencyMs = latencyMs;
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public LlmTask getTask() {
    return task;
  }

  public String getModelName() {
    return modelName;
  }

  public int getPromptTokens() {
    return promptTokens;
  }

  public int getCompletionTokens() {
    return completionTokens;
  }

  public Integer getTotalTokens() {
    return totalTokens;
  }

  public Integer getLatencyMs() {
    return latencyMs;
  }

  public Instant getCalledAt() {
    return calledAt;
  }
}
