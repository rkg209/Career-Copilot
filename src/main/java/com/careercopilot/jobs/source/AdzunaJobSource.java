package com.careercopilot.jobs.source;

import com.careercopilot.jobs.JobSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Query-driven adapter for the Adzuna search API. Given a {@link RoleQuery} it issues one search
 * per page (up to {@code page-cap}) using {@code what_or} (OR of the role keywords) and {@code
 * where} (the location), scoped to {@code country} (e.g. {@code in} for India). Enabled only when
 * app_id and app_key are configured; otherwise {@link #isEnabled()} is false and ingestion skips
 * it.
 */
@Component
public class AdzunaJobSource implements QueryableJobSource {

  private static final Logger log = LoggerFactory.getLogger(AdzunaJobSource.class);

  private final RestClient restClient;
  private final String appId;
  private final String appKey;
  private final String country;
  private final int resultsPerPage;
  private final int pageCap;
  private final String currency;

  public AdzunaJobSource(
      RestClient.Builder builder,
      @Value("${jobs.adzuna.base-url:https://api.adzuna.com}") String baseUrl,
      @Value("${jobs.adzuna.app-id:}") String appId,
      @Value("${jobs.adzuna.app-key:}") String appKey,
      @Value("${jobs.adzuna.country:in}") String country,
      @Value("${jobs.adzuna.results-per-page:50}") int resultsPerPage,
      @Value("${jobs.adzuna.page-cap:2}") int pageCap,
      @Value("${jobs.adzuna.currency:INR}") String currency) {
    this.restClient = builder.baseUrl(baseUrl).build();
    this.appId = appId;
    this.appKey = appKey;
    this.country = country;
    this.resultsPerPage = resultsPerPage;
    this.pageCap = pageCap;
    this.currency = currency;
  }

  @Override
  public JobSource id() {
    return JobSource.ADZUNA;
  }

  @Override
  public boolean isEnabled() {
    return !appId.isBlank() && !appKey.isBlank();
  }

  @Override
  public List<NormalizedJob> fetch(RoleQuery query) {
    if (!isEnabled()) {
      log.warn("Adzuna app-id/app-key not configured; skipping");
      return List.of();
    }
    String whatOr = String.join(" ", query.keywords());
    List<NormalizedJob> jobs = new ArrayList<>();
    for (int page = 1; page <= pageCap; page++) {
      List<NormalizedJob> pageJobs = fetchPage(page, whatOr, query.location());
      if (pageJobs.isEmpty()) {
        break; // no more results
      }
      jobs.addAll(pageJobs);
    }
    return jobs;
  }

  private List<NormalizedJob> fetchPage(int page, String whatOr, String where) {
    JsonNode root =
        restClient
            .get()
            .uri(
                uri ->
                    uri.path("/v1/api/jobs/{country}/search/{page}")
                        .queryParam("app_id", appId)
                        .queryParam("app_key", appKey)
                        .queryParam("results_per_page", resultsPerPage)
                        .queryParam("what_or", whatOr)
                        .queryParam("where", where)
                        .build(country, page))
            .retrieve()
            .body(JsonNode.class);
    List<NormalizedJob> jobs = new ArrayList<>();
    if (root == null || !root.hasNonNull("results")) {
      return jobs;
    }
    for (JsonNode node : root.get("results")) {
      jobs.add(toNormalized(node));
    }
    return jobs;
  }

  private NormalizedJob toNormalized(JsonNode node) {
    return new NormalizedJob(
        JobSource.ADZUNA,
        text(node, "id"),
        text(node, "title"),
        node.path("company").hasNonNull("display_name")
            ? node.path("company").get("display_name").asText()
            : null,
        location(node),
        false,
        text(node, "contract_time"),
        salary(node, "salary_min"),
        salary(node, "salary_max"),
        (node.hasNonNull("salary_min") || node.hasNonNull("salary_max")) ? currency : null,
        List.of(),
        List.of(),
        text(node, "description"),
        text(node, "redirect_url"),
        created(node),
        null,
        null);
  }

  /**
   * Builds the location string from Adzuna's {@code area} array (which includes the country, e.g.
   * {@code ["India","Karnataka","Bengaluru"]}) so country-level location searches match. Falls back
   * to {@code display_name} when {@code area} is absent.
   */
  private static String location(JsonNode node) {
    JsonNode area = node.path("location").path("area");
    if (area.isArray() && !area.isEmpty()) {
      return StreamSupport.stream(area.spliterator(), false)
          .map(JsonNode::asText)
          .collect(Collectors.joining(", "));
    }
    return node.path("location").hasNonNull("display_name")
        ? node.path("location").get("display_name").asText()
        : null;
  }

  private static String text(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : null;
  }

  private static BigDecimal salary(JsonNode node, String field) {
    return node.hasNonNull(field) ? BigDecimal.valueOf(node.get(field).asDouble()) : null;
  }

  private static Instant created(JsonNode node) {
    if (!node.hasNonNull("created")) {
      return null;
    }
    return OffsetDateTime.parse(node.get("created").asText()).toInstant();
  }
}
