package com.careercopilot.tailoring.render;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Catalog of registered LaTeX templates. Adding a template = adding a {@code .tex.mustache} file
 * under {@code templates/latex/} and registering it here.
 */
@Component
public class TemplateRegistry {

  public enum Kind {
    RESUME,
    COVER_LETTER
  }

  public record Template(
      String id, String displayName, Kind kind, String resourcePath, String previewImagePath) {}

  private static final Map<String, Template> TEMPLATES =
      Map.of(
          "classic-ats",
              new Template(
                  "classic-ats",
                  "Classic (ATS-safe)",
                  Kind.RESUME,
                  "/templates/latex/classic-ats.tex.mustache",
                  "/template-previews/classic-ats.png"),
          "modern-twocol",
              new Template(
                  "modern-twocol",
                  "Modern two-column",
                  Kind.RESUME,
                  "/templates/latex/modern-twocol.tex.mustache",
                  "/template-previews/modern-twocol.png"),
          "compact",
              new Template(
                  "compact",
                  "Compact",
                  Kind.RESUME,
                  "/templates/latex/compact.tex.mustache",
                  "/template-previews/compact.png"),
          "academic",
              new Template(
                  "academic",
                  "Academic",
                  Kind.RESUME,
                  "/templates/latex/academic.tex.mustache",
                  "/template-previews/academic.png"),
          "cover-standard",
              new Template(
                  "cover-standard",
                  "Standard cover letter",
                  Kind.COVER_LETTER,
                  "/templates/latex/cover-standard.tex.mustache",
                  null));

  /** Returns the template or throws {@link TemplateNotFoundException} (400). */
  public Template require(String templateId, Kind expectedKind) {
    Template template = TEMPLATES.get(templateId);
    if (template == null || template.kind() != expectedKind) {
      throw new TemplateNotFoundException(templateId);
    }
    return template;
  }

  public List<Template> list(Kind kind) {
    return TEMPLATES.values().stream()
        .filter(t -> t.kind() == kind)
        .sorted((a, b) -> a.id().compareTo(b.id()))
        .toList();
  }
}
