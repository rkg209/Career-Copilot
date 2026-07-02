package com.careercopilot.shared.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates every LLM JSON response against a registered draft-07 schema before it reaches any
 * caller. Schemas live in {@code src/main/resources/schemas/} and are registered by name.
 */
@Component
public class JsonSchemaValidator {

  private static final Pattern CODE_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

  private final ObjectMapper objectMapper;
  private final JsonSchemaFactory schemaFactory =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
  private final Map<String, JsonSchema> schemas = new ConcurrentHashMap<>();

  public JsonSchemaValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Result of a validation attempt; {@code payload} is non-null only when valid. */
  public record ValidationResult(boolean valid, JsonNode payload, List<String> errors) {}

  public ValidationResult validate(String rawResponse, String schemaName) {
    JsonSchema schema = loadSchema(schemaName);
    JsonNode payload;
    try {
      payload = objectMapper.readTree(extractJson(rawResponse));
    } catch (IOException e) {
      return new ValidationResult(false, null, List.of("Response is not parseable JSON"));
    }
    Set<ValidationMessage> messages = schema.validate(payload);
    if (!messages.isEmpty()) {
      return new ValidationResult(
          false, null, messages.stream().map(ValidationMessage::getMessage).toList());
    }
    return new ValidationResult(true, payload, List.of());
  }

  /** LLMs sometimes wrap JSON in markdown code fences — strip them before parsing. */
  static String extractJson(String rawResponse) {
    if (rawResponse == null) {
      return "";
    }
    Matcher m = CODE_FENCE.matcher(rawResponse);
    return m.find() ? m.group(1).strip() : rawResponse.strip();
  }

  private JsonSchema loadSchema(String schemaName) {
    return schemas.computeIfAbsent(
        schemaName,
        name -> {
          String path = "/schemas/" + name + ".schema.json";
          try (InputStream in = JsonSchemaValidator.class.getResourceAsStream(path)) {
            if (in == null) {
              throw new IllegalStateException("JSON schema not found on classpath: " + path);
            }
            return schemaFactory.getSchema(in);
          } catch (IOException e) {
            throw new UncheckedIOException("Failed to load JSON schema " + path, e);
          }
        });
  }
}
