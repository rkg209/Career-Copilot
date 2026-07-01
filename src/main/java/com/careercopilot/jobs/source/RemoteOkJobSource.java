package com.careercopilot.jobs.source;

import com.careercopilot.jobs.JobSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Keyless adapter for the public RemoteOK feed (https://remoteok.com/api). The feed is a JSON array
 * whose first element is a legal notice; every other element is a job. All RemoteOK jobs are
 * remote.
 */
@Component
public class RemoteOkJobSource implements JobSourceAdapter {

  private static final Logger log = LoggerFactory.getLogger(RemoteOkJobSource.class);
  private static final String USER_AGENT = "career-copilot/0.1 (+https://github.com/careercopilot)";

  private final RestClient restClient;

  public RemoteOkJobSource(
      RestClient.Builder builder,
      @Value("${jobs.remoteok.base-url:https://remoteok.com}") String baseUrl) {
    this.restClient = builder.baseUrl(baseUrl).defaultHeader("User-Agent", USER_AGENT).build();
  }

  @Override
  public JobSource id() {
    return JobSource.REMOTEOK;
  }

  @Override
  public List<NormalizedJob> fetch() {
    JsonNode root = restClient.get().uri("/api").retrieve().body(JsonNode.class);
    List<NormalizedJob> jobs = new ArrayList<>();
    if (root == null || !root.isArray()) {
      return jobs;
    }
    for (JsonNode node : root) {
      // Skip the leading legal-notice element and any element without a job id.
      if (node.hasNonNull("legal") || !node.hasNonNull("id")) {
        continue;
      }
      jobs.add(toNormalized(node));
    }
    return jobs;
  }

  private NormalizedJob toNormalized(JsonNode node) {
    List<String> skills = new ArrayList<>();
    JsonNode tags = node.get("tags");
    if (tags != null && tags.isArray()) {
      tags.forEach(t -> skills.add(t.asText()));
    }
    return new NormalizedJob(
        JobSource.REMOTEOK,
        node.get("id").asText(),
        text(node, "position"),
        text(node, "company"),
        text(node, "location"),
        true,
        null,
        salary(node, "salary_min"),
        salary(node, "salary_max"),
        node.hasNonNull("salary_min") ? "USD" : null,
        skills,
        List.of(),
        text(node, "description"),
        text(node, "url"),
        epochSeconds(node),
        null,
        null);
  }

  private static String text(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : null;
  }

  private static BigDecimal salary(JsonNode node, String field) {
    if (!node.hasNonNull(field) || node.get(field).asLong(0) <= 0) {
      return null;
    }
    return BigDecimal.valueOf(node.get(field).asLong());
  }

  private static Instant epochSeconds(JsonNode node) {
    if (node.hasNonNull("epoch")) {
      return Instant.ofEpochSecond(node.get("epoch").asLong());
    }
    return null;
  }
}
