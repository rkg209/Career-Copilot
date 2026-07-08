package com.careercopilot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlTextSanitizerTest {

  @Test
  void stripsTagsAndDecodesEntities() {
    String raw = "<p>We build <b>AI/ML</b> solutions &amp; platforms.</p>";
    assertThat(HtmlTextSanitizer.toPlainText(raw))
        .isEqualTo("We build AI/ML solutions & platforms.");
  }

  @Test
  void decodesDoubleEncodedEntities() {
    String raw = "Overview&amp;nbsp;&amp;nbsp;of the role";
    assertThat(HtmlTextSanitizer.toPlainText(raw)).isEqualTo("Overview  of the role");
  }

  @Test
  void convertsBlockElementsAndListItemsToLines() {
    String raw =
        "<div><p>Responsibilities:</p><ul><li>Design APIs</li><li>Mentor engineers</li></ul></div>";
    assertThat(HtmlTextSanitizer.toPlainText(raw))
        .isEqualTo("Responsibilities:\n- Design APIs\n- Mentor engineers");
  }

  @Test
  void isNoOpOnPlainText() {
    String raw = "Just plain text, no markup here.";
    assertThat(HtmlTextSanitizer.toPlainText(raw)).isEqualTo(raw);
  }

  @Test
  void returnsNullAndBlankUnchanged() {
    assertThat(HtmlTextSanitizer.toPlainText(null)).isNull();
    assertThat(HtmlTextSanitizer.toPlainText("   ")).isEqualTo("   ");
  }
}
