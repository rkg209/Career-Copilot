package com.careercopilot.tailoring.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Real-render tests: they execute the actual Tectonic binary and are skipped when it is not
 * installed (CI and the Docker image install it; locally `brew install tectonic`).
 */
class LatexRendererTest {

  private final TemplateRegistry registry = new TemplateRegistry();
  private final LatexRenderer renderer = new LatexRenderer(new ObjectMapper(), "tectonic", 120);

  private static final String RESUME_PAYLOAD =
      """
      {"candidateName":"Jane Doe",
       "contactInfo":{"email":"jane@example.com","phone":"+1 555 000 1111",
                      "location":"Berlin","linkedinUrl":"https://linkedin.com/in/janedoe"},
       "summary":"Backend engineer with 5 years of experience & a focus on 100% reliability.",
       "experience":[
         {"sourceEntryId":1,"title":"Senior Engineer","company":"Acme & Co",
          "location":"Berlin","startDate":"Jan 2022","endDate":"Present",
          "bullets":["Cut costs by 30% using #hash_tags & {braces}","Led a team of 4"]},
         {"sourceEntryId":2,"title":"Engineer","company":"Globex",
          "startDate":"Jun 2019","endDate":"Dec 2021",
          "bullets":["Built ~real-time^ pipelines with 99.9% uptime"]}],
       "skills":["Java","Spring Boot","PostgreSQL","$pecial chars"]}""";

  private static final String COVER_PAYLOAD =
      """
      {"candidateName":"Jane Doe","recipientCompany":"Acme & Co","jobTitle":"Senior Engineer",
       "salutation":"Dear Hiring Manager,",
       "bodyParagraphs":["I am excited to apply — 100% match for this role.",
                         "At Globex I built pipelines with 99.9% uptime."],
       "closing":"Sincerely,"}""";

  private static final String FULL_SECTIONS_PAYLOAD =
      """
      {"candidateName":"Jane Doe",
       "contactInfo":{"email":"jane@example.com","location":"Mumbai"},
       "summary":"Final-year student with projects & internships.",
       "education":[
         {"degree":"B.Tech. (CSE)","institution":"IIT Bombay","yearRange":"2022 -- 2026","score":"CPI 9.2"},
         {"degree":"Class XII","institution":"SMP School","university":"CBSE","yearRange":"2021","score":"94.2%"}],
       "experience":[
         {"sourceEntryId":1,"title":"SWE Intern","company":"Qualcomm",
          "startDate":"May 2025","endDate":"Jul 2025","bullets":["Automated checks 10x"]}],
       "projects":[
         {"sourceEntryId":2,"name":"Quadcopter #control","context":"Inter-IIT 2024",
          "guide":"Prof. Vachhani","startDate":"Oct 2024","endDate":"Dec 2024",
          "bullets":["Won Silver with 99% uptime & {braces}"]}],
       "positionsOfResponsibility":[
         {"sourceEntryId":3,"name":"Team Lead","context":"Mars Rover Team",
          "startDate":"May 2024","endDate":"Present","bullets":["Led 40+ members"]}],
       "skills":["Java","Python"],
       "skillGroups":[
         {"name":"Languages","skillsJoined":"C++, Python, Java"},
         {"name":"Tools","skillsJoined":"Docker, Git"}],
       "achievements":[{"text":"JEE 97.4 %ile","yearLabel":"2022"}],
       "certifications":[{"text":"AWS SAA (Amazon)","yearLabel":"2023"}],
       "courseworkJoined":"OS, DBMS, ML"}""";

  @Test
  void render_allResumeTemplates_produceValidPdfs() {
    assumeTrue(renderer.isAvailable(), "tectonic binary not installed");
    for (TemplateRegistry.Template template : registry.list(TemplateRegistry.Kind.RESUME)) {
      byte[] pdf = renderer.render(template, RESUME_PAYLOAD);
      assertThat(pdf).as("PDF for template %s", template.id()).isNotEmpty();
      assertThat(new String(pdf, 0, 5)).as("PDF magic for %s", template.id()).isEqualTo("%PDF-");
    }
  }

  @Test
  void render_allResumeTemplates_withAllNewSections_produceValidPdfs() {
    assumeTrue(renderer.isAvailable(), "tectonic binary not installed");
    for (TemplateRegistry.Template template : registry.list(TemplateRegistry.Kind.RESUME)) {
      byte[] pdf = renderer.render(template, FULL_SECTIONS_PAYLOAD);
      assertThat(pdf).as("full-sections PDF for %s", template.id()).isNotEmpty();
      assertThat(new String(pdf, 0, 5)).as("PDF magic for %s", template.id()).isEqualTo("%PDF-");
    }
  }

  @Test
  void render_coverLetterTemplate_producesValidPdf() {
    assumeTrue(renderer.isAvailable(), "tectonic binary not installed");
    TemplateRegistry.Template template =
        registry.require("cover-standard", TemplateRegistry.Kind.COVER_LETTER);
    byte[] pdf = renderer.render(template, COVER_PAYLOAD);
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
  }

  @Test
  void render_unparseablePayload_throwsRenderFailed() {
    TemplateRegistry.Template template =
        registry.require("classic-ats", TemplateRegistry.Kind.RESUME);
    assertThatThrownBy(() -> renderer.render(template, "not json"))
        .isInstanceOf(RenderFailedException.class);
  }

  @Test
  void render_missingBinary_throwsRenderFailed() {
    LatexRenderer broken = new LatexRenderer(new ObjectMapper(), "definitely-not-a-real-binary", 5);
    TemplateRegistry.Template template =
        registry.require("classic-ats", TemplateRegistry.Kind.RESUME);
    assertThatThrownBy(() -> broken.render(template, RESUME_PAYLOAD))
        .isInstanceOf(RenderFailedException.class);
  }

  @Test
  void escapeLatex_neutralizesSpecialCharacters() {
    assertThat(LatexRenderer.escapeLatex("100% of $5 & #1_test {a} ~b^ c\\d"))
        .isEqualTo(
            "100\\% of \\$5 \\& \\#1\\_test \\{a\\} \\textasciitilde{}b\\textasciicircum{}"
                + " c\\textbackslash{}d");
  }
}
