package com.careercopilot.jobs;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Structured targeting config bound from {@code jobs.targets}. Drives {@code JobRelevanceFilter}:
 * only postings whose title matches a {@code titleKeywords} entry (and, when {@code locationTerms}
 * is non-empty, whose location matches or is remote) are ingested. {@code experienceTerms} is
 * reserved for future ranking and is not a hard filter in v1. All lists default to empty; empty
 * {@code titleKeywords} is a back-compat escape hatch that lets everything through.
 */
@ConfigurationProperties(prefix = "jobs.targets")
public class JobTargetProperties {

  private List<String> titleKeywords = List.of();
  private List<String> locationTerms = List.of();
  private List<String> experienceTerms = List.of();

  public List<String> getTitleKeywords() {
    return titleKeywords;
  }

  public void setTitleKeywords(List<String> titleKeywords) {
    this.titleKeywords = titleKeywords;
  }

  public List<String> getLocationTerms() {
    return locationTerms;
  }

  public void setLocationTerms(List<String> locationTerms) {
    this.locationTerms = locationTerms;
  }

  public List<String> getExperienceTerms() {
    return experienceTerms;
  }

  public void setExperienceTerms(List<String> experienceTerms) {
    this.experienceTerms = experienceTerms;
  }
}
