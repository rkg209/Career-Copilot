package com.careercopilot.metrics.dto;

import com.careercopilot.shared.LlmTask;
import java.time.Instant;
import java.util.List;

/**
 * Per-task token/latency aggregates for the calling user only (NFR-8.2).
 *
 * <p>Note on {@code callCount}: a cache hit (see {@code LlmGateway}'s identical-prompt cache)
 * writes no usage row, and a schema-validation retry writes two — so this is a count of provider
 * calls, not a count of logical tailoring/scoring requests. Don't read it as "N resumes tailored".
 */
public record TokenUsageReport(List<TaskUsage> tasks, Instant generatedAt) {

  public record TaskUsage(
      LlmTask task,
      long promptTokens,
      long completionTokens,
      long totalTokens,
      long callCount,
      double avgLatencyMs) {}
}
