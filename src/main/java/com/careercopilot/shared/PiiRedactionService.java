package com.careercopilot.shared;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Strips PII from any text before it is sent to an LLM/embedding provider or written to logs.
 * Applied inside {@link EmbeddingGateway} (and future LlmGateway) — callers never bypass it.
 */
@Service
public class PiiRedactionService {

  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern PHONE =
      Pattern.compile(
          // US-style xxx-xxx-xxxx / xxx-xxxx
          "(?<![\\d(])(?:\\+\\d{1,3}[\\s.-]?)?(?:\\(\\d{2,4}\\)[\\s.-]?)?(?:\\d{3}[\\s.-])?\\d{3}[\\s.-]\\d{4}(?!\\d)"
              // Indian-style mobile split 5+5, e.g. "+91 98765 43210" or "98765-43210"
              + "|(?<![\\d(])(?:\\+\\d{1,3}[\\s.-]?)?\\d{5}[\\s.-]\\d{5}(?!\\d)"
              // Area-code + split local number, e.g. "(022) 2345-6789"
              + "|(?<![\\d(])\\(\\d{2,4}\\)[\\s.-]?\\d{3,4}[\\s.-]\\d{4}(?!\\d)"
              // Bare 10-12 digit run (no separators), with or without a leading country code
              + "|(?<!\\d)\\d{10,12}(?!\\d)");
  private static final Pattern SSN = Pattern.compile("(?<!\\d)\\d{3}-\\d{2}-\\d{4}(?!\\d)");

  public String redact(String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    String redacted = EMAIL.matcher(text).replaceAll("[EMAIL]");
    redacted = SSN.matcher(redacted).replaceAll("[SSN]");
    redacted = PHONE.matcher(redacted).replaceAll("[PHONE]");
    return redacted;
  }
}
