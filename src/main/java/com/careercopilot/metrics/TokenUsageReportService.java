package com.careercopilot.metrics;

import com.careercopilot.metrics.dto.TokenUsageReport;
import com.careercopilot.shared.LlmTask;
import com.careercopilot.shared.TokenUsageLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Reports per-task token/latency aggregates from {@code token_usage_log}, scoped to one user. */
@Service
public class TokenUsageReportService {

  private final TokenUsageLogRepository tokenUsageLogRepository;

  public TokenUsageReportService(TokenUsageLogRepository tokenUsageLogRepository) {
    this.tokenUsageLogRepository = tokenUsageLogRepository;
  }

  public TokenUsageReport report(UUID userId) {
    List<Object[]> rows = tokenUsageLogRepository.aggregateByTaskForUser(userId);
    List<TokenUsageReport.TaskUsage> tasks =
        rows.stream()
            .map(
                row ->
                    new TokenUsageReport.TaskUsage(
                        (LlmTask) row[0],
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue(),
                        ((Number) row[4]).longValue(),
                        row[5] != null ? ((Number) row[5]).doubleValue() : 0.0))
            .toList();
    return new TokenUsageReport(tasks, Instant.now());
  }
}
