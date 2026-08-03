package com.careercopilot.shared;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenUsageLogRepository extends JpaRepository<TokenUsageLog, Long> {

  /**
   * Per-task token/latency aggregates for one user. Row shape: {@code [task, sum(promptTokens),
   * sum(completionTokens), sum(totalTokens), count, avg(latencyMs)]}.
   */
  @Query(
      "select t.task, sum(t.promptTokens), sum(t.completionTokens), sum(t.totalTokens), "
          + "count(t), avg(t.latencyMs) "
          + "from TokenUsageLog t where t.userId = :userId group by t.task")
  List<Object[]> aggregateByTaskForUser(@Param("userId") UUID userId);
}
