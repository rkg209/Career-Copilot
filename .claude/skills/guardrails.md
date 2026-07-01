# Skill: Guardrails

## Overview

All guardrails execute inside `LlmGateway` via Spring AI Advisors. They run on every LLM call — no exceptions. The order matters:

```
User Input
  → PromptInjectionAdvisor (sanitize untrusted text BEFORE building prompt)
  → PiiRedactionAdvisor (strip PII from prompt BEFORE sending to LLM)
  → [LLM Call]
  → PiiRedactionAdvisor (strip PII from response)
  → JsonSchemaValidator (validate JSON structure)
  → GroundingValidator (verify resume references real experience)
  → Caller
```

---

## 1. PII Redaction (PiiRedactionAdvisor)

**Class:** `com.careercopilot.shared.llm.guardrails.PiiRedactionAdvisor`

Implements Spring AI `RequestResponseAdvisor`.

### Patterns to detect and redact

```java
public static final Map<String, String> PII_PATTERNS = Map.of(
    "EMAIL",       "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}",
    "PHONE_US",    "\\b(?:\\+?1[-.]?)?\\(?[0-9]{3}\\)?[-.]?[0-9]{3}[-.]?[0-9]{4}\\b",
    "SSN",         "\\b\\d{3}-\\d{2}-\\d{4}\\b",
    "CREDIT_CARD", "\\b(?:\\d{4}[-\\s]?){3}\\d{4}\\b",
    "IP_ADDRESS",  "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b",
    "DOB",         "\\b(?:0[1-9]|1[0-2])/(?:0[1-9]|[12]\\d|3[01])/(?:19|20)\\d{2}\\b",
    "ADDRESS",     "\\d+\\s+[A-Za-z0-9\\s,]+(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Lane|Ln|Drive|Dr)\\.?"
);
```

### Redaction behavior

- Replace matched text with `[REDACTED_{TYPE}]` (e.g., `[REDACTED_EMAIL]`).
- Apply to BOTH the outgoing prompt text AND the incoming LLM response.
- Log the count of redactions at DEBUG level (not the redacted values themselves).
- Do NOT redact from structured JSON keys — only from string values.

### Exception: Contact info in resume JSON

When the user explicitly provides their own contact info for the resume, PII redaction should NOT strip it from the `ResumeData.contactInfo` field. Use a `@NoPiiRedaction` marker on that field path to skip redaction.

---

## 2. Prompt Injection (PromptInjectionAdvisor)

**Class:** `com.careercopilot.shared.llm.guardrails.PromptInjectionAdvisor`

Apply to any call where **user-supplied or scraped text** (job descriptions, company names, free-text fields) is included in the prompt.

### Detection patterns

```java
static final List<Pattern> INJECTION_PATTERNS = List.of(
    // Role override attempts
    Pattern.compile("ignore (previous|above|all) instructions", CASE_INSENSITIVE),
    Pattern.compile("you are now", CASE_INSENSITIVE),
    Pattern.compile("act as (an?|a) (different|new|other)", CASE_INSENSITIVE),
    Pattern.compile("forget (everything|all) (you|your)", CASE_INSENSITIVE),
    // Prompt boundary probing
    Pattern.compile("```[\\s\\S]*?system[\\s\\S]*?```", CASE_INSENSITIVE),
    Pattern.compile("\\[INST\\]|\\[/INST\\]|<\\|system\\|>|<\\|user\\|>"),
    // Data exfiltration attempts
    Pattern.compile("print (your|the) (system )?prompt", CASE_INSENSITIVE),
    Pattern.compile("reveal (your|the) instructions", CASE_INSENSITIVE)
);
```

### Handling

- If a pattern matches: **strip the matching fragment** and replace with `[CONTENT_REMOVED]`.
- Log a WARNING with the matched pattern name (not the matched text).
- Do NOT throw an exception — continue with sanitized text.
- Increment a counter metric `careercopilot.guardrail.injection.detected`.

### JD Wrapping

Always wrap user-supplied JD text in a delimiter before including in prompts:

```java
String safeJd = "--- BEGIN JOB DESCRIPTION (untrusted) ---\n"
    + injectionAdvisor.sanitize(rawJdText)
    + "\n--- END JOB DESCRIPTION ---";
```

---

## 3. JSON Schema Validation (JsonSchemaValidator)

**Class:** `com.careercopilot.shared.llm.guardrails.JsonSchemaValidator`

Validates LLM JSON output against a registered schema before the response is returned to any caller.

### Usage

```java
// In LlmGateway method after getting response:
String rawJson = response.getResult().getOutput().getContent();
ValidationResult result = jsonSchemaValidator.validate(rawJson, LlmTask.TAILOR_RESUME);
if (!result.isValid()) {
    log.warn("LLM output schema validation failed: {}", result.getErrors());
    // Retry once
    rawJson = retryLlmCall(...);
    result = jsonSchemaValidator.validate(rawJson, LlmTask.TAILOR_RESUME);
    if (!result.isValid()) {
        throw new LlmOutputException("Invalid JSON after retry: " + result.getErrors());
    }
}
```

### Schema Registration

```java
// In @Configuration class
jsonSchemaValidator.register(LlmTask.TAILOR_RESUME, "schemas/resume-data-schema.json");
jsonSchemaValidator.register(LlmTask.EXTRACT_JD_SKILLS, "schemas/jd-skills-schema.json");
jsonSchemaValidator.register(LlmTask.SCORE_RESUME, "schemas/scoring-result-schema.json");
```

Schema files live in `src/main/resources/schemas/`.

### JSON extraction from response

LLM responses sometimes wrap JSON in markdown code fences. Extract before validating:

```java
static String extractJson(String rawResponse) {
    // Strip ```json ... ``` fences
    Matcher m = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(rawResponse);
    return m.find() ? m.group(1).strip() : rawResponse.strip();
}
```

---

## 4. Grounding Validator (GroundingValidator)

**Class:** `com.careercopilot.shared.llm.guardrails.GroundingValidator`

Applies ONLY to resume tailoring output. Verifies that every experience bullet in the LLM-generated `ResumeData` can be traced back to an experience entry in the user's corpus.

### Algorithm

```java
public GroundingResult validate(ResumeData generated, List<ExperienceEntry> userCorpus) {
    List<String> ungroundedBullets = new ArrayList<>();
    for (ExperienceBullet bullet : generated.getAllBullets()) {
        boolean grounded = userCorpus.stream().anyMatch(entry ->
            similarityScore(bullet.text(), entry.fullText()) > GROUNDING_THRESHOLD
            || entry.getKeywords().stream().anyMatch(kw -> bullet.text().contains(kw))
        );
        if (!grounded) ungroundedBullets.add(bullet.text());
    }
    return new GroundingResult(ungroundedBullets.isEmpty(), ungroundedBullets);
}
```

- `GROUNDING_THRESHOLD` = 0.65 (configurable: `careercopilot.guardrails.grounding-threshold`).
- If ungrounded bullets are found: throw `GroundingViolationException` with the list.
- The caller (TailoringService) should catch this, log it, and return a `422 Unprocessable Entity` to the user with a message like "Resume tailoring produced content that could not be verified against your experience. Please try again."

---

## 5. Custom LlmOutputException

```java
// com.careercopilot.shared.llm.LlmOutputException
public class LlmOutputException extends RuntimeException {
    private final LlmTask task;
    private final List<String> validationErrors;
    // ...
}
```

Mapped to `500 Internal Server Error` in `GlobalExceptionHandler` (do not expose LLM internals to clients — return a generic message).

---

## Guardrail Configuration

```yaml
careercopilot:
  guardrails:
    pii-redaction-enabled: true
    injection-detection-enabled: true
    grounding-threshold: 0.65
    json-validation-enabled: true
    json-retry-count: 1
```

All guardrails default to enabled. They can be disabled per-environment only via config (e.g., disable grounding in certain test scenarios), never in code.
