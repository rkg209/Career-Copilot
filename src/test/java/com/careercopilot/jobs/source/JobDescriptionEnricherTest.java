package com.careercopilot.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class JobDescriptionEnricherTest {

  private JobDescriptionEnricher enricher(int maxLength) {
    return new JobDescriptionEnricher(RestClient.builder(), true, 6, maxLength);
  }

  @Test
  void extractsDescriptionFromJobPostingJsonLd() {
    String html =
        """
        <html><head>
        <script type="application/ld+json">
        {"@context":"https://schema.org","@type":"JobPosting",
         "title":"Backend Engineer",
         "description":"<p>We build <b>scalable</b> systems for millions of users worldwide, \
        working across the full stack from infrastructure to product. We value ownership, \
        curiosity, and clear communication above all else on this team.</p>\
        <ul><li>Own services end to end, from design through on-call</li>\
        <li>Mentor junior engineers and review their code regularly</li>\
        <li>Partner with product and design on the roadmap</li></ul>"}
        </script>
        </head><body><div class="nav">Home</div></body></html>
        """;

    var result = enricher(8000).extract(html, 50);

    assertThat(result).isPresent();
    assertThat(result.get())
        .contains("We build scalable systems")
        .contains("- Own services end to end");
  }

  @Test
  void extractsFromArrayOfJsonLdEntries() {
    String html =
        """
        <html><head>
        <script type="application/ld+json">
        [{"@type":"BreadcrumbList"},
         {"@type":"JobPosting","description":"%s"}]
        </script>
        </head><body></body></html>
        """
            .formatted(
                "Full role description text that is definitely long enough to count. ".repeat(5));

    var result = enricher(8000).extract(html, 50);

    assertThat(result).isPresent();
    assertThat(result.get()).contains("Full role description text");
  }

  @Test
  void fallsBackToContentHeuristicWhenNoJsonLd() {
    String longText = "Responsibilities include building great products. ".repeat(20);
    String html =
        "<html><body><nav>Home About</nav>"
            + "<div class=\"job-description-container\">"
            + longText
            + "</div></body></html>";

    var result = enricher(8000).extract(html, 50);

    assertThat(result).isPresent();
    assertThat(result.get()).contains("Responsibilities include building great products.");
  }

  @Test
  void returnsEmptyWhenExtractedTextIsNotMeaningfullyLonger() {
    String html =
        """
        <html><head>
        <script type="application/ld+json">
        {"@type":"JobPosting","description":"Short blurb"}
        </script>
        </head><body></body></html>
        """;

    var result = enricher(8000).extract(html, 500);

    assertThat(result).isEmpty();
  }

  @Test
  void returnsEmptyWhenNoStructuredOrContentDataFound() {
    String html = "<html><body><div class=\"unrelated\">nothing useful here</div></body></html>";

    var result = enricher(8000).extract(html, 50);

    assertThat(result).isEmpty();
  }

  @Test
  void truncatesToConfiguredMaxLength() {
    String longDescription = "word ".repeat(5000);
    String html =
        "<html><head><script type=\"application/ld+json\">"
            + "{\"@type\":\"JobPosting\",\"description\":\""
            + longDescription
            + "\"}"
            + "</script></head><body></body></html>";

    var result = enricher(500).extract(html, 50);

    assertThat(result).isPresent();
    assertThat(result.get().length()).isLessThanOrEqualTo(500);
  }
}
