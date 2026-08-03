package com.careercopilot.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.careercopilot.shared.LlmTask;
import com.careercopilot.shared.TokenUsageLogRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenUsageReportServiceTest {

  @Mock private TokenUsageLogRepository tokenUsageLogRepository;

  private TokenUsageReportService service() {
    return new TokenUsageReportService(tokenUsageLogRepository);
  }

  @Test
  void report_mapsAggregateRows_perTask() {
    UUID userId = UUID.randomUUID();
    when(tokenUsageLogRepository.aggregateByTaskForUser(userId))
        .thenReturn(
            List.of(
                new Object[] {LlmTask.TAILORING, 1200L, 400L, 1600L, 3L, 850.5},
                new Object[] {LlmTask.EXTRACTION, 300L, 100L, 400L, 5L, 210.0}));

    var report = service().report(userId);

    assertThat(report.tasks()).hasSize(2);
    var tailoring =
        report.tasks().stream()
            .filter(t -> t.task() == LlmTask.TAILORING)
            .findFirst()
            .orElseThrow();
    assertThat(tailoring.promptTokens()).isEqualTo(1200L);
    assertThat(tailoring.completionTokens()).isEqualTo(400L);
    assertThat(tailoring.totalTokens()).isEqualTo(1600L);
    assertThat(tailoring.callCount()).isEqualTo(3L);
    assertThat(tailoring.avgLatencyMs()).isEqualTo(850.5);
  }

  @Test
  void report_returnsEmptyList_whenUserHasNoUsageYet() {
    UUID userId = UUID.randomUUID();
    when(tokenUsageLogRepository.aggregateByTaskForUser(userId)).thenReturn(List.of());

    var report = service().report(userId);

    assertThat(report.tasks()).isEmpty();
  }
}
