package com.careercopilot.intel;

import com.careercopilot.shared.PiiRedactionService;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic, authoritative scrubber for free text written to {@code company_interview_intel}.
 * Delegates email/phone/SSN stripping to the shared {@link PiiRedactionService}, then additionally
 * strips URLs and likely person names before truncating. Never adds content — only removes or
 * replaces it with a placeholder token.
 */
@Component
public class PiiScrubber {

  private static final int MAX_LENGTH = 2000;

  // Scheme-qualified URLs, plus bare "domain/path" mentions (e.g. "linkedin.com/in/janedoe") —
  // the mandatory trailing "/path" keeps this from false-positiving on "Node.js" or version
  // strings like "3.10", which never have a path segment after them.
  private static final Pattern URL =
      Pattern.compile(
          "https?://\\S+" + "|\\b(?:[a-z0-9-]+\\.)+[a-z]{2,}/[\\w./?=&%-]*",
          Pattern.CASE_INSENSITIVE);

  /** Two consecutive Title-Cased words look like a person's name unless they're a known term. */
  private static final Pattern CAPITALIZED_BIGRAM =
      Pattern.compile("\\b[A-Z][a-z]+ [A-Z][a-z]+\\b");

  private static final Set<String> BIGRAM_STOPLIST =
      Set.of(
          "system design",
          "rate limiter",
          "load balancer",
          "data structures",
          "object oriented",
          "time complexity",
          "space complexity",
          "hash map",
          "binary search",
          "dynamic programming",
          "machine learning",
          "behavioral round",
          "phone screen",
          "take home",
          "final round",
          "software engineer",
          "product manager",
          "data scientist",
          "new york",
          "san francisco",
          "united states",
          "big o");

  private final PiiRedactionService piiRedactionService;

  public PiiScrubber(PiiRedactionService piiRedactionService) {
    this.piiRedactionService = piiRedactionService;
  }

  public String scrub(String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    String scrubbed = piiRedactionService.redact(text);
    scrubbed = URL.matcher(scrubbed).replaceAll("[URL]");
    scrubbed = redactLikelyNames(scrubbed);
    return scrubbed.length() > MAX_LENGTH ? scrubbed.substring(0, MAX_LENGTH) : scrubbed;
  }

  private String redactLikelyNames(String text) {
    Matcher matcher = CAPITALIZED_BIGRAM.matcher(text);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;
    while (matcher.find()) {
      String match = matcher.group();
      result.append(text, lastEnd, matcher.start());
      result.append(
          BIGRAM_STOPLIST.contains(match.toLowerCase(java.util.Locale.ROOT)) ? match : "[PERSON]");
      lastEnd = matcher.end();
    }
    result.append(text.substring(lastEnd));
    return result.toString();
  }
}
