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
 * Keyless adapter for company job boards hosted on Ashby. For each configured board token it reads
 * the public JSON at {@code /posting-api/job-board/{token}}, whose {@code jobs} array holds one
 * entry per opening. Locations come through verbatim (e.g. "Remote - India", "Bengaluru"), so
 * country-level location search works. Each board is fetched in its own try/catch so a bad token
 * never aborts the others.
 */
@Component
public class AshbyJobSource implements JobSourceAdapter {

  private static final Logger log = LoggerFactory.getLogger(AshbyJobSource.class);

  private final RestClient restClient;
  private final List<String> companies;

  public AshbyJobSource(
      RestClient.Builder builder,
      @Value("${jobs.ashby.base-url:https://api.ashbyhq.com}") String baseUrl,
      @Value("${jobs.ashby.companies:}") List<String> companies) {
    this.restClient = builder.baseUrl(baseUrl).build();
    this.companies = companies;
  }

  @Override
  public JobSource id() {
    return JobSource.ASHBY;
  }

  @Override
  public List<NormalizedJob> fetch() {
    List<NormalizedJob> jobs = new ArrayList<>();
    for (String company : companies) {
      if (company == null || company.isBlank()) {
        continue;
      }
      try {
        jobs.addAll(fetchBoard(company.trim()));
      } catch (Exception e) {
        log.warn("Ashby board '{}' failed: {}", company, e.getMessage());
      }
    }
    return jobs;
  }

  private List<NormalizedJob> fetchBoard(String company) {
    JsonNode root =
        restClient
            .get()
            .uri("/posting-api/job-board/{company}?includeCompensation=true", company)
            .retrieve()
            .body(JsonNode.class);
    List<NormalizedJob> jobs = new ArrayList<>();
    if (root == null || !root.hasNonNull("jobs")) {
      return jobs;
    }
    for (JsonNode node : root.get("jobs")) {
      jobs.add(toNormalized(node, company));
    }
    return jobs;
  }

  private NormalizedJob toNormalized(JsonNode node, String company) {
    String location = text(node, "location");
    boolean remote =
        node.path("isRemote").asBoolean(false)
            || (location != null && location.toLowerCase().contains("remote"));
    String description =
        node.hasNonNull("descriptionPlain")
            ? node.get("descriptionPlain").asText()
            : text(node, "description");
    return new NormalizedJob(
        JobSource.ASHBY,
        company + ":" + node.get("id").asText(),
        text(node, "title"),
        companyName(node, company),
        location,
        remote,
        text(node, "employmentType"),
        null,
        null,
        null,
        List.of(),
        List.of(),
        description != null ? description : text(node, "title"),
        text(node, "jobUrl"),
        publishedAt(node),
        null,
        null);
  }

  private static String companyName(JsonNode node, String company) {
    String name = text(node, "organizationName");
    return name != null ? name : company;
  }

  private static String text(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : null;
  }

  private static Instant publishedAt(JsonNode node) {
    if (!node.hasNonNull("publishedAt")) {
      return null;
    }
    try {
      return OffsetDateTime.parse(node.get("publishedAt").asText()).toInstant();
    } catch (Exception e) {
      return null;
    }
  }
}
