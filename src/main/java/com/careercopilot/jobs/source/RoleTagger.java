package com.careercopilot.jobs.source;

import com.careercopilot.jobs.RoleProperties;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Assigns role-preset keys to a posting by matching its title (falling back to the first ~500 chars
 * of full text) against each role's configured keywords. A posting may match several roles. Used to
 * tag whole-board (ATS) postings, which arrive without a query role. Deterministic,
 * case-insensitive.
 */
@Component
public class RoleTagger {

  private static final int FULLTEXT_SCAN_LIMIT = 500;

  private final RoleProperties roleProperties;

  public RoleTagger(RoleProperties roleProperties) {
    this.roleProperties = roleProperties;
  }

  /** Returns every role key whose keywords match the posting; empty if none. */
  public Set<String> tag(NormalizedJob job) {
    String haystack = haystack(job);
    Set<String> matched = new LinkedHashSet<>();
    if (haystack.isBlank()) {
      return matched;
    }
    roleProperties
        .getRoles()
        .forEach(
            (key, role) -> {
              if (containsAny(haystack, role.getKeywords())) {
                matched.add(key);
              }
            });
    return matched;
  }

  private static String haystack(NormalizedJob job) {
    String title = job.title();
    if (title != null && !title.isBlank()) {
      return title.toLowerCase(Locale.ROOT);
    }
    String text = job.fullText();
    if (text == null) {
      return "";
    }
    text = text.toLowerCase(Locale.ROOT);
    return text.length() > FULLTEXT_SCAN_LIMIT ? text.substring(0, FULLTEXT_SCAN_LIMIT) : text;
  }

  private static boolean containsAny(String haystack, java.util.List<String> needles) {
    if (needles == null) {
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
}
