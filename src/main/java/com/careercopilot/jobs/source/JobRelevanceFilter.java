package com.careercopilot.jobs.source;

import com.careercopilot.jobs.JobTargetProperties;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic, config-driven relevance gate applied at ingestion time (before persistence). A
 * posting is kept when:
 *
 * <ul>
 *   <li><b>Title match (required):</b> its title — falling back to the first ~500 chars of full
 *       text — contains at least one configured {@code titleKeywords} entry. An empty keyword list
 *       lets everything through (back-compat escape hatch).
 *   <li><b>Location match (required only when {@code locationTerms} is non-empty):</b> the posting
 *       is remote (and not country-restricted away from every allowed location, see below), or its
 *       location contains at least one configured {@code locationTerms} entry. A null location on a
 *       non-remote posting is dropped when location terms are configured.
 *   <li><b>Remote-restriction check:</b> a remote posting whose location text names a configured
 *       {@code remoteRestrictedTerms} entry (e.g. "Remote - USA") and does <em>not</em> also
 *       mention an allowed {@code locationTerms} entry is dropped — "remote" there means restricted
 *       to that country's candidates, not open to ours.
 * </ul>
 *
 * <p>Experience terms are intentionally <em>not</em> a hard filter in v1 (many entry-level roles
 * are titled plainly, e.g. "Software Engineer"); they are reserved for a future ranking stage.
 */
@Component
public class JobRelevanceFilter {

  private static final int FULLTEXT_SCAN_LIMIT = 500;

  private final JobTargetProperties targets;

  public JobRelevanceFilter(JobTargetProperties targets) {
    this.targets = targets;
  }

  public boolean isRelevant(NormalizedJob job) {
    return matchesTitle(job) && matchesLocation(job);
  }

  private boolean matchesTitle(NormalizedJob job) {
    List<String> keywords = targets.getTitleKeywords();
    if (keywords == null || keywords.isEmpty()) {
      return true;
    }
    String haystack = lower(job.title());
    if (haystack.isBlank()) {
      String fullText = lower(job.fullText());
      haystack =
          fullText.length() > FULLTEXT_SCAN_LIMIT
              ? fullText.substring(0, FULLTEXT_SCAN_LIMIT)
              : fullText;
    }
    return containsAny(haystack, keywords);
  }

  private boolean matchesLocation(NormalizedJob job) {
    List<String> terms = targets.getLocationTerms();
    if (terms == null || terms.isEmpty()) {
      return true;
    }
    if (job.remote()) {
      return !isRestrictedElsewhere(job, terms);
    }
    return containsAny(lower(job.location()), terms);
  }

  /**
   * A remote posting is restricted-elsewhere when its location text names a configured
   * remote-restricted country/region (e.g. "Remote - USA") and does not also mention an allowed
   * *place* term. Blank location text is treated as unrestricted/globally remote. The generic
   * "remote" entry in {@code locationTerms} is excluded from the allow-check here — every remote
   * posting's location text contains that word, so it would trivially defeat the restriction.
   */
  private boolean isRestrictedElsewhere(NormalizedJob job, List<String> allowedTerms) {
    List<String> restricted = targets.getRemoteRestrictedTerms();
    if (restricted == null || restricted.isEmpty()) {
      return false;
    }
    String location = lower(job.location());
    if (location.isBlank()) {
      return false;
    }
    List<String> allowedPlaces =
        allowedTerms.stream()
            .filter(t -> t != null && !t.trim().equalsIgnoreCase("remote"))
            .collect(Collectors.toList());
    if (containsAny(location, allowedPlaces)) {
      return false;
    }
    return containsAny(location, restricted);
  }

  private static boolean containsAny(String haystack, List<String> needles) {
    if (haystack.isBlank()) {
      return false;
    }
    for (String needle : needles) {
      if (needle == null) {
        continue;
      }
      String n = needle.trim().toLowerCase(Locale.ROOT);
      if (!n.isEmpty() && haystack.contains(n)) {
        return true;
      }
    }
    return false;
  }

  private static String lower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }
}
