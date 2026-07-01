# Spec: {Phase Name}

## Header

| Field | Value |
|---|---|
| Phase | {phase number and name} |
| Status | DRAFT \| APPROVED \| IMPLEMENTED |
| Version | 1.0 |
| Author | {author} |
| Last Updated | {date} |

---

## Goal

One paragraph describing what this phase accomplishes, why it matters, and the primary user-facing outcome.

---

## Scope

### In Scope
- {concrete deliverable 1}
- {concrete deliverable 2}

### Out of Scope
- {explicitly excluded item 1}
- {explicitly excluded item 2}

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-{N} | {functional requirement description} |
| NFR-{N} | {non-functional requirement description} |

---

## Data Model Changes

### New Tables

```sql
-- Table: {table_name}
CREATE TABLE {table_name} (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- columns...
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V{N} | `V{N}__{description}.sql` | {what it does} |

---

## API Changes

### New Endpoints

#### `POST /api/v1/{resource}`

**Request:**
```json
{
  "field1": "string",
  "field2": 0
}
```

**Response (200):**
```json
{
  "id": "uuid",
  "field1": "string"
}
```

**Error cases:**
- `400` — validation failure
- `401` — unauthenticated
- `403` — not owner
- `404` — resource not found

#### `GET /api/v1/{resource}/{id}`

*(repeat for each endpoint)*

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `{Name}Controller` | `com.careercopilot.{feature}` | HTTP boundary, input validation |
| `{Name}Service` | `com.careercopilot.{feature}` | Business logic |
| `{Name}Repository` | `com.careercopilot.{feature}` | Data access |
| `{Name}Dto` | `com.careercopilot.{feature}.dto` | API data transfer |

### Interaction Flow

```
Client
  → {Name}Controller.{method}(dto)
  → {Name}Service.{method}(dto)
    → {Name}Repository.{query}()
    → [LlmGateway.{task}() if LLM involved]
  → returns ResponseDto
  → Controller returns ResponseEntity<ResponseDto>
```

---

## LLM Tasks

*(Skip this section entirely if this phase has no LLM calls.)*

### Task: {TaskName}

| Field | Value |
|---|---|
| Model Tier | EXTRACTION \| TAILORING \| SCORING |
| LlmTask enum | `LlmTask.{TASK_NAME}` |
| Max input tokens | {N} |
| Max output tokens | {N} |

**Prompt outline:**
```
System: {system prompt summary}
User: {user prompt template with {placeholders}}
```

**Output JSON schema:**
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "field1": { "type": "string" }
  },
  "required": ["field1"]
}
```

**Guardrails applied:**
- [ ] PiiRedactionAdvisor
- [ ] PromptInjectionAdvisor (if JD text is input)
- [ ] GroundingValidator (if resume tailoring)
- [ ] JsonSchemaValidator

---

## Rendering Tasks

*(Skip this section if this phase has no resume rendering.)*

### Template Used

| Field | Value |
|---|---|
| Template ID | `{template-id}` |
| Template file | `src/main/resources/templates/latex/{template-id}.tex.mustache` |

**JSON input schema for renderer:**
```json
{
  "candidateName": "string",
  "contactInfo": { "email": "string", "phone": "string", "linkedin": "string" },
  "summary": "string",
  "experience": [
    {
      "title": "string",
      "company": "string",
      "startDate": "YYYY-MM",
      "endDate": "YYYY-MM | present",
      "bullets": ["string"]
    }
  ],
  "education": [{ "degree": "string", "institution": "string", "year": "string" }],
  "skills": ["string"]
}
```

---

## Test Plan

### Unit Tests

| Test class | What it tests |
|---|---|
| `{Name}ServiceTest` | Service logic with mocked repository and LlmGateway |
| `{Name}ControllerTest` | Controller validation, auth, response codes (MockMvc) |

### Integration Tests

| Test class | What it tests |
|---|---|
| `{Name}IntegrationTest` | Full stack with Testcontainers Postgres+pgvector, mocked LlmGateway |

### Guardrail Tests *(if LLM tasks present)*

| Test | Scenario |
|---|---|
| `{Task}PiiRedactionTest` | Confirm PII is stripped before LLM call |
| `{Task}PromptInjectionTest` | Confirm injected instructions in JD are neutralized |
| `{Task}GroundingTest` | Confirm invented experience is rejected |
| `{Task}SchemaValidationTest` | Confirm malformed LLM JSON triggers retry/exception |

---

## Deploy Steps

### Pre-deploy Checklist

- [ ] `./mvnw test` — all tests green
- [ ] `./mvnw spotless:check` — formatting clean
- [ ] Flyway migration files reviewed (additive only)
- [ ] No secrets committed
- [ ] Required env vars added to DigitalOcean App spec

### Environment Variables Required

| Variable | Description | Example |
|---|---|---|
| `{VAR_NAME}` | {what it controls} | `{example value}` |

### DigitalOcean Deploy Steps

1. Push to `main` branch — GitHub Actions CI runs.
2. On green CI: DigitalOcean App Platform auto-deploys (or trigger manual deploy).
3. Flyway migrations run automatically on startup.
4. Smoke test: hit `GET /actuator/health` → expect `{"status":"UP"}`.
5. Verify new endpoints via curl or Postman against prod URL.

---

## Definition of Done

- [ ] Spec status set to APPROVED before implementation begins
- [ ] All listed API endpoints implemented and returning correct responses
- [ ] All Flyway migrations applied cleanly (no errors in startup logs)
- [ ] All unit tests pass
- [ ] All integration tests pass (Testcontainers)
- [ ] Guardrail tests pass (if LLM tasks present)
- [ ] `/review` checklist passes
- [ ] CLAUDE.md updated to reflect new current phase
- [ ] Deployed to DigitalOcean and smoke-tested
- [ ] Spec status set to IMPLEMENTED
