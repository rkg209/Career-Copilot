package com.careercopilot.jobs.source;

import com.careercopilot.jobs.JobSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Keyless adapter for company job boards hosted on Greenhouse. For each configured board token it
 * reads the public JSON at {@code /v1/boards/{token}/jobs?content=true}. Locations come through
 * verbatim (e.g. "Remote - India", "Bengaluru, India"), so country-level location search works.
 * Each board is fetched in its own try/catch so a bad token never aborts the others.
 */
@Component
public class GreenhouseJobSource implements JobSourceAdapter {

  private static final Logger log = LoggerFactory.getLogger(GreenhouseJobSource.class);

  private final RestClient restClient;
  private final List<String> companies;

  public GreenhouseJobSource(
      RestClient.Builder builder,
      @Value("${jobs.greenhouse.base-url:https://boards-api.greenhouse.io}") String baseUrl,
      @Value("${jobs.greenhouse.companies:}") List<String> companies) {
    this.restClient = builder.baseUrl(baseUrl).build();
    this.companies = companies;
  }

  @Override
  public JobSource id() {
    return JobSource.GREENHOUSE;
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
        log.warn("Greenhouse board '{}' failed: {}", company, e.getMessage());
      }
    }
    return jobs;
  }

  private List<NormalizedJob> fetchBoard(String company) {
    String displayName = boardName(company);
    JsonNode root =
        restClient
            .get()
            .uri("/v1/boards/{company}/jobs?content=true", company)
            .retrieve()
            .body(JsonNode.class);
    List<NormalizedJob> jobs = new ArrayList<>();
    if (root == null || !root.hasNonNull("jobs")) {
      return jobs;
    }
    for (JsonNode node : root.get("jobs")) {
      jobs.add(toNormalized(node, company, displayName));
    }
    return jobs;
  }

  /** Fetches the board's human-readable name; falls back to the token on failure. */
  private String boardName(String company) {
    try {
      JsonNode board =
          restClient.get().uri("/v1/boards/{company}", company).retrieve().body(JsonNode.class);
      if (board != null && board.hasNonNull("name")) {
        return board.get("name").asText();
      }
    } catch (Exception e) {
      log.debug("Greenhouse board name lookup failed for '{}': {}", company, e.getMessage());
    }
    return company;
  }

  private NormalizedJob toNormalized(JsonNode node, String company, String displayName) {
    String location = node.path("location").path("name").asText(null);
    boolean remote = location != null && location.toLowerCase().contains("remote");
    return new NormalizedJob(
        JobSource.GREENHOUSE,
        company + ":" + node.get("id").asText(),
        text(node, "title"),
        displayName,
        location,
        remote,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        node.hasNonNull("content") ? node.get("content").asText() : text(node, "title"),
        text(node, "absolute_url"),
        updatedAt(node),
        null,
        null);
  }

  private static String text(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : null;
  }

  private static Instant updatedAt(JsonNode node) {
    if (!node.hasNonNull("updated_at")) {
      return null;
    }
    try {
      return OffsetDateTime.parse(node.get("updated_at").asText()).toInstant();
    } catch (Exception e) {
      return null;
    }
  }
}
