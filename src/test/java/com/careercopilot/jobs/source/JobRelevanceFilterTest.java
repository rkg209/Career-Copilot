package com.careercopilot.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.jobs.JobSource;
import com.careercopilot.jobs.JobTargetProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobRelevanceFilterTest {

  private JobRelevanceFilter filter(List<String> titleKeywords, List<String> locationTerms) {
    return filter(titleKeywords, locationTerms, List.of());
  }

  private JobRelevanceFilter filter(
      List<String> titleKeywords, List<String> locationTerms, List<String> remoteRestrictedTerms) {
    JobTargetProperties props = new JobTargetProperties();
    props.setTitleKeywords(titleKeywords);
    props.setLocationTerms(locationTerms);
    props.setRemoteRestrictedTerms(remoteRestrictedTerms);
    return new JobRelevanceFilter(props);
  }

  private NormalizedJob job(String title, String location, boolean remote) {
    return new NormalizedJob(
        JobSource.GREENHOUSE,
        "1",
        title,
        "Acme",
        location,
        remote,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        title == null ? "" : title,
        "https://x",
        null,
        null,
        null);
  }

  @Test
  void keepsMatchingTitle() {
    JobRelevanceFilter f = filter(List.of("machine learning", "software engineer"), List.of());
    assertThat(f.isRelevant(job("Senior Machine Learning Engineer", "Remote", true))).isTrue();
  }

  @Test
  void dropsOffTargetTitle() {
    JobRelevanceFilter f = filter(List.of("machine learning", "software engineer"), List.of());
    assertThat(f.isRelevant(job("Product Designer", "Remote", true))).isFalse();
    assertThat(f.isRelevant(job("Enterprise Sales Executive", "Remote", true))).isFalse();
  }

  @Test
  void emptyKeywordListPassesEverything() {
    JobRelevanceFilter f = filter(List.of(), List.of());
    assertThat(f.isRelevant(job("Anything At All", "Mars", false))).isTrue();
  }

  @Test
  void dropsNonRemoteOffLocationWhenLocationTermsSet() {
    JobRelevanceFilter f = filter(List.of("software engineer"), List.of("india", "singapore"));
    assertThat(f.isRelevant(job("Software Engineer", "Berlin, Germany", false))).isFalse();
  }

  @Test
  void keepsRemoteRegardlessOfLocation() {
    JobRelevanceFilter f = filter(List.of("software engineer"), List.of("india"));
    assertThat(f.isRelevant(job("Software Engineer", "Berlin, Germany", true))).isTrue();
  }

  @Test
  void keepsMatchingLocation() {
    JobRelevanceFilter f = filter(List.of("software engineer"), List.of("india"));
    assertThat(f.isRelevant(job("Software Engineer", "Bengaluru, India", false))).isTrue();
  }

  @Test
  void dropsNullLocationNonRemoteWhenLocationTermsSet() {
    JobRelevanceFilter f = filter(List.of("software engineer"), List.of("india"));
    assertThat(f.isRelevant(job("Software Engineer", null, false))).isFalse();
  }

  @Test
  void nullTitleIsSafeAndDroppedWhenKeywordsSet() {
    JobRelevanceFilter f = filter(List.of("software engineer"), List.of());
    assertThat(f.isRelevant(job(null, "Remote", true))).isFalse();
  }

  @Test
  void dropsRemoteRestrictedToAnotherCountry() {
    JobRelevanceFilter f =
        filter(List.of("software engineer"), List.of("india", "remote"), List.of("usa", "us"));
    assertThat(f.isRelevant(job("Software Engineer", "Remote - USA", true))).isFalse();
  }

  @Test
  void keepsRemoteRestrictedTermThatAlsoMentionsAnAllowedLocation() {
    JobRelevanceFilter f =
        filter(List.of("software engineer"), List.of("india"), List.of("usa", "us"));
    assertThat(f.isRelevant(job("Software Engineer", "Remote (USA or India)", true))).isTrue();
  }

  @Test
  void keepsGenericRemoteWithNoCountryQualifier() {
    JobRelevanceFilter f =
        filter(List.of("software engineer"), List.of("india"), List.of("usa", "us"));
    assertThat(f.isRelevant(job("Software Engineer", "Remote", true))).isTrue();
  }

  @Test
  void keepsBlankLocationRemoteEvenWithRestrictedTermsConfigured() {
    JobRelevanceFilter f =
        filter(List.of("software engineer"), List.of("india"), List.of("usa", "us"));
    assertThat(f.isRelevant(job("Software Engineer", null, true))).isTrue();
  }

  @Test
  void keepsRemoteRestrictedElsewhereWhenNoRestrictedTermsConfigured() {
    JobRelevanceFilter f = filter(List.of("software engineer"), List.of("india"), List.of());
    assertThat(f.isRelevant(job("Software Engineer", "Remote - USA", true))).isTrue();
  }
}
