---
description: Self-review the current diff against CLAUDE.md rules. Check architecture, LLM, resume, guardrail, privacy, testing, and security rules.
---

Read CLAUDE.md completely before starting the review.

Run `git diff HEAD` (or `git diff main` if on a feature branch) to get the current changes.

Review the diff against each rule category below. For each item, mark PASS, FAIL, or N/A.

---

## 1. Architecture Rules

- [ ] **Package-by-feature**: New Java files are in `com.careercopilot.{feature}` packages, not in a flat structure.
- [ ] **Layering**: No controller calls a repository directly. No repository contains business logic.
- [ ] **DTOs at boundary**: Controllers receive/return DTOs (not JPA entities). Entities are not serialized to JSON directly (no `@JsonProperty` on entity fields).
- [ ] **No business logic in controllers**: Controllers only validate input and call service methods.
- [ ] **Shared utilities in `shared/`**: Any class used by 2+ features is in `com.careercopilot.shared`.

## 2. LLM Rules

- [ ] **All LLM calls via LlmGateway**: No direct `ChatClient` usage outside `LlmGateway`. Search diff for `ChatClient` — it should only appear in `LlmGateway.java`.
- [ ] **No hardcoded model names**: Search diff for any string matching `gpt-`, `claude-`, `mistral`, `llama`, `openrouter` — these must NOT appear as string literals in Java code. Only in `application.yml`.
- [ ] **No hardcoded provider URLs**: No `https://api.openai.com` or `https://openrouter.ai` in Java code.
- [ ] **Token logging**: Every new `LlmGateway` method calls `logTokenUsage()`.
- [ ] **Tiered model config used**: New LLM tasks specify a tier enum value, not a model name.

## 3. Resume Rule

- [ ] **No LaTeX in LLM prompts**: Search diff for `\begin{`, `\end{`, `\section{`, `\textbf{` — these must NOT appear in any prompt string.
- [ ] **LLM output is JSON only**: No LLM task prompts ask for "formatted text", "a resume", or "write this in LaTeX."
- [ ] **Rendering is separate**: PDF generation only occurs in `ResumeRenderingService`, never in a service that also calls `LlmGateway`.

## 4. Guardrail Rules

- [ ] **PII redaction applied**: Every `LlmGateway` method includes `PiiRedactionAdvisor` in its advisor list.
- [ ] **Prompt injection handled**: Any method that takes user-supplied or scraped text includes `PromptInjectionAdvisor`.
- [ ] **Grounding validated**: Resume tailoring methods call `GroundingValidator` on the output.
- [ ] **JSON schema validation**: Every LLM output is validated against a registered schema before being returned.

## 5. Privacy Rules

- [ ] **Per-user isolation**: Every repository query that returns user data has `userId` as a filter. Search diff for repository methods — do they all filter by user?
- [ ] **No cross-user data**: No API endpoint returns data from multiple users mixed together.
- [ ] **Anonymization before shared pool**: Any write to `company_interview_intel` goes through `AnonymizationService.anonymize()`.
- [ ] **Intel staging respected**: No code reads from `company_intel_staging` in a user-facing API endpoint.

## 6. Testing Rules

- [ ] **Testcontainers for integration tests**: New integration test classes use `@Testcontainers` with a PostgreSQL container, not H2 or mocked DB.
- [ ] **LLM mocked in tests**: No test class injects a real `LlmGateway` — it should be `@MockBean LlmGateway`.
- [ ] **New endpoints have tests**: Every new `@GetMapping`/`@PostMapping`/etc. has at least one test in an `IntegrationTest` class.
- [ ] **Test naming convention**: Integration test classes end in `IntegrationTest`, unit tests end in `Test`.

## 7. Security Checklist

- [ ] **JWT verification on protected endpoints**: New controller endpoints (non-public) are covered by the JWT filter. No endpoint accidentally skips `SecurityConfig` permit rules.
- [ ] **Ownership checks**: If an endpoint returns a specific resource (e.g., `/resume/{id}`), the service verifies the authenticated user owns that resource before returning it.
- [ ] **No secrets in code**: Search diff for `password`, `secret`, `apikey`, `api_key`, `token` as literal string values — these must not appear in Java or config files checked into git.
- [ ] **Input validation**: New request DTOs use Jakarta validation annotations (`@NotNull`, `@Size`, `@Email`, etc.) and controllers use `@Valid`.

---

## Report Format

Print a table:

```
CATEGORY          | RESULT | NOTES
------------------|--------|------
Architecture      | PASS   |
LLM Rules         | PASS   |
Resume Rule       | N/A    | No LLM tasks in this diff
Guardrails        | FAIL   | PiiRedactionAdvisor missing from ExtractionService.extractSkills()
Privacy           | PASS   |
Testing           | FAIL   | JobPostingControllerTest missing - new endpoint has no test
Security          | PASS   |
```

Then:
- List each FAIL with file path and line number.
- If all PASS (or N/A): "Review passed. Safe to run /deploy-checkpoint."
- If any FAIL: "Fix the above issues before deploying."
