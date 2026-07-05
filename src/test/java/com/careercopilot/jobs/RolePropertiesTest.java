package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

class RolePropertiesTest {

  @Test
  void bindsRolesKeywordsAndSearchLocations() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("jobs.search-locations", "India,Remote");
    env.setProperty("jobs.roles.ai-ml-engineer.label", "AI / ML Engineer");
    env.setProperty("jobs.roles.ai-ml-engineer.keywords", "machine learning,ml engineer");
    env.setProperty("jobs.roles.quant.label", "Quant / Trading");
    env.setProperty("jobs.roles.quant.keywords", "quant,trading systems");

    Binder binder = new Binder(ConfigurationPropertySources.get(env));
    RoleProperties props = binder.bind("jobs", RoleProperties.class).get();

    assertThat(props.getSearchLocations()).containsExactly("India", "Remote");
    assertThat(props.getRoles()).containsKeys("ai-ml-engineer", "quant");
    assertThat(props.getRoles().get("ai-ml-engineer").getLabel()).isEqualTo("AI / ML Engineer");
    assertThat(props.getRoles().get("ai-ml-engineer").getKeywords())
        .containsExactly("machine learning", "ml engineer");
    assertThat(props.getRoles().get("quant").getKeywords())
        .containsExactly("quant", "trading systems");
  }
}
