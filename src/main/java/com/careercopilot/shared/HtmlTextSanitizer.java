package com.careercopilot.shared;

import java.util.Set;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * Converts raw HTML (as returned by some source ATS APIs, e.g. Greenhouse's {@code content} field)
 * into clean, human-readable plain text — tags stripped, entities decoded, block-level elements
 * turned into line breaks so downstream section parsing still works. A no-op on text that contains
 * no markup. Walks the DOM directly (rather than round-tripping through HTML serialization, which
 * re-escapes and un-escapes entities symmetrically and never makes progress) so it also unwinds
 * double-encoded entities from sources that escape their HTML twice (e.g. literal {@code
 * &amp;nbsp;}).
 */
public final class HtmlTextSanitizer {

  private static final Set<String> BLOCK_TAGS =
      Set.of("p", "div", "tr", "li", "h1", "h2", "h3", "h4", "h5", "h6", "br");

  private HtmlTextSanitizer() {}

  public static String toPlainText(String raw) {
    if (raw == null || raw.isBlank()) {
      return raw;
    }
    Document doc = Jsoup.parse(raw);
    StringBuilder sb = new StringBuilder();
    NodeTraversor.traverse(
        new NodeVisitor() {
          @Override
          public void head(Node node, int depth) {
            if (node instanceof TextNode textNode) {
              sb.append(textNode.getWholeText());
            } else if (node instanceof Element el && el.tagName().equals("li")) {
              sb.append("- ");
            }
          }

          @Override
          public void tail(Node node, int depth) {
            if (node instanceof Element el && BLOCK_TAGS.contains(el.tagName())) {
              sb.append('\n');
            }
          }
        },
        doc.body());

    String text = Parser.unescapeEntities(sb.toString(), false);
    text = text.replace('\u00A0', ' ');
    text =
        text.lines()
            .map(String::strip)
            .collect(Collectors.joining("\n"))
            .replaceAll("\n{3,}", "\n\n");
    return text.strip();
  }
}
