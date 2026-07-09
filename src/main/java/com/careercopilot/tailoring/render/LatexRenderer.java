package com.careercopilot.tailoring.render;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samskivert.mustache.Mustache;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deterministic, LLM-free rendering: validated JSON payload → Mustache-filled LaTeX → Tectonic
 * subprocess → PDF bytes. All user-supplied strings are LaTeX-escaped before template fill; the
 * JSON payload is the single source of truth (CLAUDE.md resume rule).
 */
@Component
public class LatexRenderer {

  private static final Logger log = LoggerFactory.getLogger(LatexRenderer.class);

  private final ObjectMapper objectMapper;
  private final Mustache.Compiler compiler = Mustache.compiler().escapeHTML(false).defaultValue("");
  private final String tectonicBinary;
  private final int timeoutSeconds;

  public LatexRenderer(
      ObjectMapper objectMapper,
      @Value("${tailoring.tectonic.binary}") String tectonicBinary,
      @Value("${tailoring.tectonic.timeout-seconds}") int timeoutSeconds) {
    this.objectMapper = objectMapper;
    this.tectonicBinary = tectonicBinary;
    this.timeoutSeconds = timeoutSeconds;
  }

  /** Renders the JSON payload through the given template. Throws on timeout/failure. */
  public byte[] render(TemplateRegistry.Template template, String jsonPayload) {
    String tex = fillTemplate(template, jsonPayload);
    Path workDir = null;
    try {
      workDir = Files.createTempDirectory("cc-render-");
      Path texFile = workDir.resolve("document.tex");
      Files.writeString(texFile, tex, StandardCharsets.UTF_8);
      runTectonic(workDir, texFile);
      Path pdfFile = workDir.resolve("document.pdf");
      if (!Files.exists(pdfFile)) {
        throw new RenderFailedException("Tectonic produced no PDF output");
      }
      return Files.readAllBytes(pdfFile);
    } catch (IOException e) {
      throw new RenderFailedException("PDF rendering failed: " + e.getMessage(), e);
    } finally {
      cleanup(workDir);
    }
  }

  /** True when the Tectonic binary is runnable — used to guard render tests and health checks. */
  public boolean isAvailable() {
    try {
      Process process = new ProcessBuilder(tectonicBinary, "--version").start();
      return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private String fillTemplate(TemplateRegistry.Template template, String jsonPayload) {
    Map<String, Object> context;
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> escaped =
          (Map<String, Object>) escapeValues(objectMapper.readValue(jsonPayload, Map.class));
      context = escaped;
    } catch (JsonProcessingException e) {
      throw new RenderFailedException("Stored JSON payload is not parseable", e);
    }
    if (context.get("skills") instanceof List<?> skills && !skills.isEmpty()) {
      List<String> parts = skills.stream().map(String::valueOf).toList();
      context.put("skillsJoined", String.join(" \\textbullet{} ", parts));
    }
    // Section presence flags so templates render a section header only when it has content.
    for (String section :
        List.of(
            "experience",
            "projects",
            "positionsOfResponsibility",
            "education",
            "skillGroups",
            "achievements",
            "certifications")) {
      if (context.get(section) instanceof List<?> items && !items.isEmpty()) {
        String flag = "has" + Character.toUpperCase(section.charAt(0)) + section.substring(1);
        context.put(flag, true);
      }
    }
    try (InputStream in = LatexRenderer.class.getResourceAsStream(template.resourcePath())) {
      if (in == null) {
        throw new RenderFailedException("Template resource missing: " + template.resourcePath());
      }
      return compiler.compile(new InputStreamReader(in, StandardCharsets.UTF_8)).execute(context);
    } catch (IOException e) {
      throw new RenderFailedException("Failed to read template " + template.id(), e);
    }
  }

  private void runTectonic(Path workDir, Path texFile) throws IOException {
    ProcessBuilder builder =
        new ProcessBuilder(
            tectonicBinary, "--outdir", workDir.toString(), texFile.getFileName().toString());
    builder.directory(workDir.toFile());
    builder.redirectErrorStream(true);
    Process process = builder.start();
    String output;
    try {
      boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
      output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!finished) {
        process.destroyForcibly();
        throw new RenderTimeoutException("Tectonic did not finish within " + timeoutSeconds + "s");
      }
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new RenderFailedException("Rendering interrupted", e);
    }
    if (process.exitValue() != 0) {
      log.warn("Tectonic failed (exit {}): {}", process.exitValue(), tail(output));
      throw new RenderFailedException("Tectonic failed: " + tail(output));
    }
  }

  /** Recursively LaTeX-escapes every string value; keys and non-strings pass through. */
  @SuppressWarnings("unchecked")
  private static Object escapeValues(Object node) {
    if (node instanceof Map<?, ?> map) {
      Map<String, Object> escaped = new LinkedHashMap<>();
      map.forEach((k, v) -> escaped.put(String.valueOf(k), escapeValues(v)));
      return escaped;
    }
    if (node instanceof List<?> list) {
      List<Object> escaped = new ArrayList<>(list.size());
      list.forEach(item -> escaped.add(escapeValues(item)));
      return escaped;
    }
    if (node instanceof String s) {
      return escapeLatex(s);
    }
    return node;
  }

  static String escapeLatex(String value) {
    StringBuilder sb = new StringBuilder(value.length());
    for (char c : value.toCharArray()) {
      switch (c) {
        case '\\' -> sb.append("\\textbackslash{}");
        case '&', '%', '$', '#', '_', '{', '}' -> sb.append('\\').append(c);
        case '~' -> sb.append("\\textasciitilde{}");
        case '^' -> sb.append("\\textasciicircum{}");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  private static String tail(String output) {
    if (output == null) {
      return "";
    }
    String trimmed = output.strip();
    return trimmed.length() <= 500 ? trimmed : trimmed.substring(trimmed.length() - 500);
  }

  private static void cleanup(Path workDir) {
    if (workDir == null) {
      return;
    }
    try (var paths = Files.walk(workDir)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                  // best-effort temp cleanup
                }
              });
    } catch (IOException ignored) {
      // best-effort temp cleanup
    }
  }
}
