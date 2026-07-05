package com.careercopilot.jobs.source;

import com.careercopilot.jobs.JobSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Query-driven adapter for the Jooble aggregator. Given a {@link RoleQuery} it POSTs {@code
 * {keywords, location}} to {@code /api/{key}} and maps the returned {@code jobs} array into {@link
 * NormalizedJob}. Enabled only when a free API key is configured. Jooble returns a single result
 * page per call; {@code page-cap} is reserved for future paging via the {@code page} body field.
 */
@Component
public class JoobleJobSource implements QueryableJobSource {

  private static final Logger log = LoggerFactory.getLogger(JoobleJobSource.class);

  private final RestClient restClient;
  private final String apiKey;

  public JoobleJobSource(
      RestClient.Builder builder,
      @Value("${jobs.jooble.base-url:https://jooble.org}") String baseUrl,
      @Value("${jobs.jooble.api-key:}") String apiKey) {
    this.restClient = builder.baseUrl(baseUrl).build();
    this.apiKey = apiKey;
  }

  @Override
  public JobSource id() {
    return JobSource.JOOBLE;
  }

  @Override
  public boolean isEnabled() {
    return !apiKey.isBlank();
  }

  @Override
  public List<NormalizedJob> fetch(RoleQuery query) {
    if (!isEnabled()) {
      log.warn("Jooble api-key not configured; skipping");
      return List.of();
    }
    JsonNode root =
        restClient
            .post()
            .uri("/api/{key}", apiKey)
            .body(
                Map.of(
                    "keywords", String.join(", ", query.keywords()),
                    "location", query.location()))
            .retrieve()
            .body(JsonNode.class);
    List<NormalizedJob> jobs = new ArrayList<>();
    if (root == null || !root.hasNonNull("jobs") || !root.get("jobs").isArray()) {
      return jobs;
    }
    for (JsonNode node : root.get("jobs")) {
      jobs.add(toNormalized(node));
    }
    return jobs;
  }

  private NormalizedJob toNormalized(JsonNode node) {
    String location = text(node, "location");
    boolean remote = location != null && location.toLowerCase().contains("remote");
    String snippet = text(node, "snippet");
    return new NormalizedJob(
        JobSource.JOOBLE,
        text(node, "id"),
        text(node, "title"),
        text(node, "company"),
        location,
        remote,
        text(node, "type"),
        null,
        null,
        null,
        List.of(),
        List.of(),
        snippet != null ? snippet : text(node, "title"),
        text(node, "link"),
        updated(node),
        null,
        null);
  }

  private static String text(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : null;
  }

  private static Instant updated(JsonNode node) {
    if (!node.hasNonNull("updated")) {
      return null;
    }
    try {
      return OffsetDateTime.parse(node.get("updated").asText()).toInstant();
    } catch (Exception e) {
      return null;
    }
  }
}
