package com.careercopilot.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.jobs.JobSource;
import com.careercopilot.jobs.RoleProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoleTaggerTest {

  private RoleTagger tagger() {
    RoleProperties props = new RoleProperties();
    props.setRoles(
        new java.util.LinkedHashMap<>(
            Map.of(
                "ai-ml-engineer", role("machine learning", "ml engineer"),
                "software-engineer", role("software engineer", "backend engineer"))));
    return new RoleTagger(props);
  }

  private RoleProperties.Role role(String... keywords) {
    RoleProperties.Role r = new RoleProperties.Role();
    r.setKeywords(List.of(keywords));
    return r;
  }

  private NormalizedJob job(String title) {
    return new NormalizedJob(
        JobSource.GREENHOUSE,
        "1",
        title,
        "Acme",
        "Remote",
        true,
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
  void tagsMatchingRole() {
    assertThat(tagger().tag(job("Senior Machine Learning Engineer")))
        .containsExactly("ai-ml-engineer");
  }

  @Test
  void tagsMultipleRolesWhenTitleMatchesSeveral() {
    // "software engineer" matches software-engineer; add ML wording to also match ai-ml-engineer.
    assertThat(tagger().tag(job("Machine Learning Software Engineer")))
        .containsExactlyInAnyOrder("ai-ml-engineer", "software-engineer");
  }

  @Test
  void emptyForOffTarget() {
    assertThat(tagger().tag(job("Product Designer"))).isEmpty();
  }

  @Test
  void caseInsensitive() {
    assertThat(tagger().tag(job("BACKEND ENGINEER"))).containsExactly("software-engineer");
  }

  @Test
  void nullTitleSafe() {
    assertThat(tagger().tag(job(null))).isEmpty();
  }
}
