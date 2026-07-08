package com.careercopilot.jobs.source;

import com.careercopilot.shared.HtmlTextSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Best-effort fetch of the real job description from a posting's original URL, for sources (Adzuna,
 * Jooble) whose search API only ever returns a short snippet. Two extraction strategies, tried in
 * order:
 *
 * <ol>
 *   <li>{@code schema.org/JobPosting} JSON-LD — most job boards and ATS pages embed this for Google
 *       Jobs SEO, so it's the most reliable source of the real description when present.
 *   <li>A heuristic fallback: the element with the most text among common content containers.
 * </ol>
 *
 * Every failure mode (blocked, JS-rendered SPA with no static content, timeout, malformed HTML)
 * degrades to {@link Optional#empty()} — the caller keeps whatever snippet it already had. This
 * must never fail ingestion for a source that could always fetch its own data fine.
 */
@Component
public class JobDescriptionEnricher {

  private static final Logger log = LoggerFactory.getLogger(JobDescriptionEnricher.class);

  private static final String[] CONTENT_SELECTORS = {
    "[class*=job-description]",
    "[class*=jobDescription]",
    "[id*=job-description]",
    "[class*=description]",
    "[id*=description]",
    "article",
    "main",
    "[role=main]"
  };

  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final boolean enabled;
  private final int maxLength;

  public JobDescriptionEnricher(
      RestClient.Builder builder,
      @Value("${jobs.enrichment.enabled:true}") boolean enabled,
      @Value("${jobs.enrichment.timeout-seconds:6}") int timeoutSeconds,
      @Value("${jobs.enrichment.max-length:8000}") int maxLength) {
    this.enabled = enabled;
    this.maxLength = maxLength;
    this.restClient =
        builder
            .requestFactory(clientHttpRequestFactory(Duration.ofSeconds(timeoutSeconds)))
            .defaultHeader(
                "User-Agent",
                "Mozilla/5.0 (compatible; CareerCopilotBot/1.0; +https://careercopilot.example/bot)")
            .build();
  }

  private static org.springframework.http.client.ClientHttpRequestFactory clientHttpRequestFactory(
      Duration timeout) {
    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) timeout.toMillis());
    factory.setReadTimeout((int) timeout.toMillis());
    return factory;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Fetches {@code url} and returns a cleaned, longer description if extraction succeeds and yields
   * meaningfully more text than {@code existingLength}; otherwise {@link Optional#empty()}.
   */
  public Optional<String> fetchFullDescription(String url, int existingLength) {
    if (!enabled || url == null || url.isBlank()) {
      return Optional.empty();
    }
    String html;
    try {
      html = restClient.get().uri(url).retrieve().body(String.class);
    } catch (Exception e) {
      log.debug("Enrichment fetch failed for {}: {}", url, e.getMessage());
      return Optional.empty();
    }
    if (html == null || html.isBlank()) {
      return Optional.empty();
    }
    try {
      return extract(html, existingLength);
    } catch (Exception e) {
      log.debug("Enrichment extraction failed for {}: {}", url, e.getMessage());
      return Optional.empty();
    }
  }

  /** Package-visible for testing without a real HTTP round-trip. */
  Optional<String> extract(String html, int existingLength) {
    Document doc = Jsoup.parse(html);
    String text = fromJsonLd(doc).or(() -> fromContentHeuristic(doc)).orElse(null);
    if (text == null) {
      return Optional.empty();
    }
    if (text.length() > maxLength) {
      text = text.substring(0, maxLength).strip();
    }
    // Only replace the existing text if this is a meaningful improvement, not layout/nav noise.
    if (text.length() < existingLength + 200) {
      return Optional.empty();
    }
    return Optional.of(text);
  }

  private Optional<String> fromJsonLd(Document doc) {
    for (Element script : doc.select("script[type=application/ld+json]")) {
      JsonNode root;
      try {
        root = objectMapper.readTree(script.data());
      } catch (Exception e) {
        continue;
      }
      Optional<String> description = jobPostingDescription(root);
      if (description.isPresent()) {
        return description.map(HtmlTextSanitizer::toPlainText);
      }
    }
    return Optional.empty();
  }

  private Optional<String> jobPostingDescription(JsonNode node) {
    if (node.isArray()) {
      for (JsonNode item : node) {
        Optional<String> found = jobPostingDescription(item);
        if (found.isPresent()) {
          return found;
        }
      }
      return Optional.empty();
    }
    if (node.isObject()) {
      JsonNode type = node.get("@type");
      boolean isJobPosting =
          type != null
              && (type.isTextual()
                  ? "JobPosting".equalsIgnoreCase(type.asText())
                  : type.isArray() && containsIgnoreCase(type, "JobPosting"));
      if (isJobPosting && node.hasNonNull("description")) {
        return Optional.of(node.get("description").asText());
      }
      if (node.hasNonNull("@graph")) {
        return jobPostingDescription(node.get("@graph"));
      }
    }
    return Optional.empty();
  }

  private static boolean containsIgnoreCase(JsonNode arrayNode, String value) {
    for (JsonNode item : arrayNode) {
      if (value.equalsIgnoreCase(item.asText())) {
        return true;
      }
    }
    return false;
  }

  private Optional<String> fromContentHeuristic(Document doc) {
    Element best = null;
    int bestLength = 0;
    for (String selector : CONTENT_SELECTORS) {
      for (Element candidate : doc.select(selector)) {
        int length = candidate.text().length();
        if (length > bestLength) {
          best = candidate;
          bestLength = length;
        }
      }
    }
    if (best == null) {
      return Optional.empty();
    }
    return Optional.of(HtmlTextSanitizer.toPlainText(best.html()));
  }
}
