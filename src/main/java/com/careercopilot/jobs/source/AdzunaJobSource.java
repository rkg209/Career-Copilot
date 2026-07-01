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
 * Adapter for the Adzuna search API. Built now but only invoked when {@code ADZUNA} is present in
 * {@code jobs.adapters.enabled} and app_id/app_key are configured. The {@code country} config
 * scopes results to a region (e.g. {@code in} for India); {@code pages} controls how many result
 * pages are pulled per run.
 */
@Component
public class AdzunaJobSource implements JobSourceAdapter {

  private static final Logger log = LoggerFactory.getLogger(AdzunaJobSource.class);

  private final RestClient restClient;
  private final String appId;
  private final String appKey;
  private final String country;
  private final int resultsPerPage;
  private final int pages;
  private final String currency;

  public AdzunaJobSource(
      RestClient.Builder builder,
      @Value("${jobs.adzuna.base-url:https://api.adzuna.com}") String baseUrl,
      @Value("${jobs.adzuna.app-id:}") String appId,
      @Value("${jobs.adzuna.app-key:}") String appKey,
      @Value("${jobs.adzuna.country:us}") String country,
      @Value("${jobs.adzuna.results-per-page:50}") int resultsPerPage,
      @Value("${jobs.adzuna.pages:3}") int pages,
      @Value("${jobs.adzuna.currency:USD}") String currency) {
    this.restClient = builder.baseUrl(baseUrl).build();
    this.appId = appId;
    this.appKey = appKey;
    this.country = country;
    this.resultsPerPage = resultsPerPage;
    this.pages = pages;
    this.currency = currency;
  }

  @Override
  public JobSource id() {
    return JobSource.ADZUNA;
  }

  @Override
  public List<NormalizedJob> fetch() {
    if (appId.isBlank() || appKey.isBlank()) {
      throw new IllegalStateException("Adzuna app-id/app-key not configured");
    }
    List<NormalizedJob> jobs = new ArrayList<>();
    for (int page = 1; page <= pages; page++) {
      List<NormalizedJob> pageJobs = fetchPage(page);
      if (pageJobs.isEmpty()) {
        break; // no more results
      }
      jobs.addAll(pageJobs);
    }
    return jobs;
  }

  private List<NormalizedJob> fetchPage(int page) {
    JsonNode root =
        restClient
            .get()
            .uri(
                uri ->
                    uri.path("/v1/api/jobs/{country}/search/{page}")
                        .queryParam("app_id", appId)
                        .queryParam("app_key", appKey)
                        .queryParam("results_per_page", resultsPerPage)
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
