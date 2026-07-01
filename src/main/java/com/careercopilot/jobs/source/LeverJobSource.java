package com.careercopilot.jobs.source;

import com.careercopilot.jobs.JobSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Keyless adapter for company job boards hosted on Lever. For each configured company token it
 * reads the public JSON at {@code /v0/postings/{token}?mode=json}. Built and enabled via config; no
 * default companies ship because valid Lever board tokens vary per employer. Each board is fetched
 * in its own try/catch so a bad token never aborts the others.
 */
@Component
public class LeverJobSource implements JobSourceAdapter {

  private static final Logger log = LoggerFactory.getLogger(LeverJobSource.class);

  private final RestClient restClient;
  private final List<String> companies;

  public LeverJobSource(
      RestClient.Builder builder,
      @Value("${jobs.lever.base-url:https://api.lever.co}") String baseUrl,
      @Value("${jobs.lever.companies:}") List<String> companies) {
    this.restClient = builder.baseUrl(baseUrl).build();
    this.companies = companies;
  }

  @Override
  public JobSource id() {
    return JobSource.LEVER;
  }

  @Override
  public List<NormalizedJob> fetch() {
    List<NormalizedJob> jobs = new ArrayList<>();
    for (String company : companies) {
      if (company.isBlank()) {
        continue;
      }
      try {
        jobs.addAll(fetchBoard(company.trim()));
      } catch (Exception e) {
        log.warn("Lever board '{}' failed: {}", company, e.getMessage());
      }
    }
    return jobs;
  }

  private List<NormalizedJob> fetchBoard(String company) {
    JsonNode root =
        restClient
            .get()
            .uri("/v0/postings/{company}?mode=json", company)
            .retrieve()
            .body(JsonNode.class);
    List<NormalizedJob> jobs = new ArrayList<>();
    if (root == null || !root.isArray()) {
      return jobs; // an error payload comes back as an object, not an array
    }
    for (JsonNode node : root) {
      jobs.add(toNormalized(node, company));
    }
    return jobs;
  }

  private NormalizedJob toNormalized(JsonNode node, String company) {
    JsonNode categories = node.path("categories");
    String location = categories.path("location").asText(null);
    boolean remote = location != null && location.toLowerCase().contains("remote");
    String fullText =
        node.hasNonNull("descriptionPlain")
            ? node.get("descriptionPlain").asText()
            : text(node, "description");
    return new NormalizedJob(
        JobSource.LEVER,
        company + ":" + node.get("id").asText(),
        text(node, "text"),
        company,
        location,
        remote,
        categories.path("commitment").asText(null),
        null,
        null,
        null,
        List.of(),
        List.of(),
        fullText != null ? fullText : text(node, "text"),
        text(node, "hostedUrl"),
        createdAt(node),
        null,
        null);
  }

  private static String text(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : null;
  }

  private static Instant createdAt(JsonNode node) {
    return node.hasNonNull("createdAt")
        ? Instant.ofEpochMilli(node.get("createdAt").asLong())
        : null;
  }
}
