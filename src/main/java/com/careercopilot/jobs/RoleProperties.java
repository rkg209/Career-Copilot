package com.careercopilot.jobs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Curated role presets and the locations searched for each. Bound from {@code jobs.roles} and
 * {@code jobs.search-locations}. Each preset key (e.g. {@code ai-ml-engineer}) maps to a display
 * label and the search keywords used both to query aggregators and to keyword-tag whole-board
 * postings. Order is preserved (LinkedHashMap) so the UI picker is stable. Unknown {@code jobs.*}
 * keys are ignored.
 */
@ConfigurationProperties(prefix = "jobs")
public class RoleProperties {

  private Map<String, Role> roles = new LinkedHashMap<>();
  private List<String> searchLocations = List.of();

  public Map<String, Role> getRoles() {
    return roles;
  }

  public void setRoles(Map<String, Role> roles) {
    this.roles = roles;
  }

  public List<String> getSearchLocations() {
    return searchLocations;
  }

  public void setSearchLocations(List<String> searchLocations) {
    this.searchLocations = searchLocations;
  }

  /** A single role preset. */
  public static class Role {
    private String label;
    private List<String> keywords = List.of();

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public List<String> getKeywords() {
      return keywords;
    }

    public void setKeywords(List<String> keywords) {
      this.keywords = keywords;
    }
  }
}
