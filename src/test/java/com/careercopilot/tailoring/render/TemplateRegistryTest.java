package com.careercopilot.tailoring.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TemplateRegistryTest {

  private final TemplateRegistry registry = new TemplateRegistry();

  @Test
  void require_returnsRegisteredResumeTemplate() {
    TemplateRegistry.Template template =
        registry.require("classic-ats", TemplateRegistry.Kind.RESUME);
    assertThat(template.resourcePath()).endsWith("classic-ats.tex.mustache");
  }

  @Test
  void require_unknownId_throwsTemplateNotFound() {
    assertThatThrownBy(() -> registry.require("does-not-exist", TemplateRegistry.Kind.RESUME))
        .isInstanceOf(TemplateNotFoundException.class);
  }

  @Test
  void require_kindMismatch_throwsTemplateNotFound() {
    // A cover-letter template cannot be used to render a resume.
    assertThatThrownBy(() -> registry.require("cover-standard", TemplateRegistry.Kind.RESUME))
        .isInstanceOf(TemplateNotFoundException.class);
  }

  @Test
  void list_returnsFourResumeTemplatesAndOneCover() {
    assertThat(registry.list(TemplateRegistry.Kind.RESUME)).hasSize(4);
    assertThat(registry.list(TemplateRegistry.Kind.COVER_LETTER)).hasSize(1);
  }

  @Test
  void allRegisteredTemplates_existOnClasspath() {
    for (TemplateRegistry.Kind kind : TemplateRegistry.Kind.values()) {
      for (TemplateRegistry.Template template : registry.list(kind)) {
        assertThat(TemplateRegistry.class.getResource(template.resourcePath()))
            .as("template resource %s", template.resourcePath())
            .isNotNull();
      }
    }
  }
}
