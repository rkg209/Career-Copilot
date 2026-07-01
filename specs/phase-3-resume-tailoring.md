# Spec: Phase 3 — Resume Tailoring (RAG + JSON + LaTeX)

## Header

| Field | Value |
|---|---|
| Phase | 3 — Resume Tailoring (RAG + JSON + LaTeX templates) |
| Status | DRAFT |
| Version | 1.0 |
| Author | Rahul |
| Last Updated | 2026-07-01 |

> **Schema reconciliation note:** New tables use `BIGSERIAL` PKs; `user_id` FKs are `UUID`. Confirm
> before APPROVED.

---

## Goal

The core value of the product: pick a job posting and get a tailored, well-formatted resume PDF grounded
strictly in the user's real experience. This phase wires the full RAG pipeline — retrieve the most
relevant experience entries from the user's private corpus, feed them (plus profile + JD) to a tiered
`ChatClient` that emits **structured JSON only**, validate that JSON against a registered schema, enforce
grounding (nothing invented), render the JSON deterministically through a selected LaTeX template to PDF
via Tectonic, and persist the result. Cover-letter generation is available on demand through the same
pipeline. All four guardrails (PII, injection, grounding, JSON) go live here.

---

## Scope

### In Scope
- `LlmGateway` (Spring AI `ChatClient`) with tiered model config (`EXTRACTION`/`TAILORING` tiers).
- Four guardrail advisors, executed inside `LlmGateway`: `PiiRedactionAdvisor`,
  `PromptInjectionAdvisor`, `GroundingValidator`, `JsonSchemaValidator` (retry-once-then-throw).
- RAG retrieval: embed the JD, `findTopKByVector(userId, jdVector, k)` over the user's corpus.
- Tailoring task: profile + retrieved experience + JD → `ResumeJsonPayload` (structured JSON).
- Template catalog: 4–5 LaTeX templates registered in `TemplateRegistry`; `.tex.mustache` files.
- Deterministic render: JSON → selected template → Tectonic → PDF; JSON persisted even if render fails.
- Cover-letter task (on demand) through the same gateway + a cover-letter template.
- Endpoints: tailor, list resumes, get resume, download PDF, generate cover letter.
- React: template picker, "Tailor for this job" action, resume history, PDF download.

### Out of Scope
- ATS scoring of the output (Phase 4).
- Editing the generated JSON in the UI before render (v2).
- Multiple simultaneous template renders in one call (one template per request).
- Any LaTeX in an LLM prompt — prompts request structured data only (hard rule).

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-3.1 | A user selects a JD + template and receives a tailored resume PDF. |
| FR-3.2 | Tailoring output references only the user's actual experience entries (grounded). |
| FR-3.3 | The LLM returns structured JSON only; rendering is deterministic and LLM-free. |
| FR-3.4 | JD text pasted by users is sanitized for prompt injection before prompt assembly. |
| FR-3.5 | PII is redacted from all LLM inputs/outputs before logging or persistence. |
| FR-3.6 | Malformed LLM JSON triggers one retry, then a structured 502 error. |
| FR-3.7 | A user can generate a cover letter for a JD on demand. |
| NFR-3.1 | Resume JSON is always persisted even when PDF rendering fails (retryable). |
| NFR-3.2 | Every tailoring call logs task, model, and token counts. |
| NFR-3.3 | TAILORING tier uses the strong model; retrieval/embedding uses the cheap tier. |

---

## Data Model Changes

### New Tables

```sql
-- V8__resume_versions.sql
CREATE TABLE resume_versions (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_posting_id    BIGINT      NOT NULL REFERENCES job_postings (id) ON DELETE RESTRICT,
    template_id       TEXT        NOT NULL,
    json_payload      JSONB       NOT NULL,
    pdf_bytes         BYTEA,                                  -- NULL if render failed; retryable
    llm_model         TEXT        NOT NULL,
    token_count       INT         NOT NULL CHECK (token_count >= 0),
    render_failed     BOOLEAN     NOT NULL DEFAULT false,
    render_error      TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_resume_versions_user_id        ON resume_versions (user_id);
CREATE INDEX idx_resume_versions_job_posting_id ON resume_versions (job_posting_id);
CREATE INDEX idx_resume_versions_created_at     ON resume_versions (user_id, created_at DESC);
CREATE INDEX idx_resume_versions_render_failed  ON resume_versions (render_failed) WHERE render_failed = true;
```

```sql
-- V9__cover_letters.sql
CREATE TABLE cover_letters (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_posting_id    BIGINT      NOT NULL REFERENCES job_postings (id) ON DELETE RESTRICT,
    resume_version_id BIGINT      REFERENCES resume_versions (id) ON DELETE SET NULL,
    template_id       TEXT        NOT NULL,
    json_payload      JSONB       NOT NULL,
    pdf_bytes         BYTEA,
    llm_model         TEXT        NOT NULL,
    token_count       INT         NOT NULL CHECK (token_count >= 0),
    render_failed     BOOLEAN     NOT NULL DEFAULT false,
    render_error      TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cover_letters_user_id        ON cover_letters (user_id);
CREATE INDEX idx_cover_letters_job_posting_id ON cover_letters (job_posting_id);
CREATE INDEX idx_cover_letters_created_at     ON cover_letters (user_id, created_at DESC);
```

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V8 | `V8__resume_versions.sql` | `resume_versions` (JSONB payload + BYTEA pdf) |
| V9 | `V9__cover_letters.sql` | `cover_letters` |

---

## API Changes

### `POST /api/v1/tailoring/tailor` — OWNER_ONLY
**Request:**
```json
{ "jobPostingId": 42, "templateId": "classic-ats" }
```
**Response (201):**
```json
{
  "resumeVersionId": 17,
  "templateId": "classic-ats",
  "jobPostingId": 42,
  "llmModel": "…",
  "tokenCount": 2380,
  "renderStatus": "RENDERED",
  "createdAt": "2026-07-01T10:30:00Z"
}
```
**Error cases:** `400 VALIDATION_FAILED`; `400 INVALID_TEMPLATE_ID`; `400 PROMPT_INJECTION_DETECTED`;
`401`; `403 ACCESS_DENIED`; `404 RESOURCE_NOT_FOUND` (job posting); `422 INSUFFICIENT_EXPERIENCE`
(user has no experience entries); `422 GROUNDING_FAILURE`; `502 LLM_SCHEMA_ERROR`; `502 LLM_API_ERROR`;
`503 LLM_UNAVAILABLE`; `503 RENDERING_TIMEOUT`; `500 RENDERING_FAILED`. On render failure the
`resumeVersionId` is returned in `details[]` (JSON persisted; retry via the PDF endpoint).

### `GET /api/v1/tailoring/resumes` — OWNER_ONLY
Paginated (`sort=createdAt`, default `createdAt,desc`). Returns resume metadata (no PDF bytes).

### `GET /api/v1/tailoring/resumes/{id}` — OWNER_ONLY
Returns metadata + `jsonPayload`. **Errors:** `403`; `404`.

### `GET /api/v1/tailoring/resumes/{id}/pdf` — OWNER_ONLY
Returns `application/pdf` with `Content-Disposition`. If `pdf_bytes` is NULL, **re-renders on demand**
from `json_payload`. **Errors:** `403`; `404`; `503 RENDERING_TIMEOUT`; `500 RENDERING_FAILED`.

### `POST /api/v1/tailoring/cover-letter` — OWNER_ONLY
**Request:**
```json
{ "jobPostingId": 42, "templateId": "cover-standard", "resumeVersionId": 17, "tone": "professional" }
```
`resumeVersionId` and `tone` are optional. **Response (201):** cover-letter metadata (mirrors tailor).
**Errors:** same family as tailor.

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `TailoringController` | `com.careercopilot.tailoring` | Tailor, list, get, pdf, cover-letter endpoints |
| `TailoringService` | `com.careercopilot.tailoring` | Orchestrates RAG → LLM → validate → render → persist |
| `CoverLetterService` | `com.careercopilot.tailoring` | Cover-letter variant of the pipeline |
| `ResumeVersionRepository` / `CoverLetterRepository` | `com.careercopilot.tailoring` | Persistence |
| `LlmGateway` | `com.careercopilot.shared` | `ChatClient` wrapper; runs all advisors; token logging |
| `PiiRedactionAdvisor` / `PromptInjectionAdvisor` / `GroundingValidator` / `JsonSchemaValidator` | `com.careercopilot.shared.guardrail` | Guardrails (see `.claude/skills/guardrails.md`) |
| `TemplateRegistry` | `com.careercopilot.tailoring.render` | Catalog of registered templates |
| `LatexRenderer` | `com.careercopilot.tailoring.render` | Mustache → `.tex` → Tectonic → PDF (see `.claude/skills/latex-resume-renderer.md`) |
| DTOs | `com.careercopilot.tailoring.dto` | `TailorRequest`, `ResumeResponse`, `CoverLetterRequest` |

### Interaction Flow

```
POST /tailoring/tailor
  → TailoringController.tailor(req, principal)
  → TailoringService.tailor(userId, jobPostingId, templateId)
    → TemplateRegistry.require(templateId)                         -- 400 INVALID_TEMPLATE_ID
    → load JobPosting (404) + CandidateProfile
    → EmbeddingGateway.embed(jd.fullText) → jdVector
    → ExperienceRepository.findTopKByVector(userId, jdVector, k)   -- 422 if corpus empty
    → LlmGateway.call(TAILORING, prompt{profile, retrievedExperience, sanitizedJd})
        · PromptInjectionAdvisor sanitizes jd.fullText             -- 400 PROMPT_INJECTION_DETECTED
        · PiiRedactionAdvisor redacts inputs/outputs before log
        · JsonSchemaValidator validates ResumeJsonPayload (retry once) -- 502 LLM_SCHEMA_ERROR
        · GroundingValidator checks every claim maps to a retrieved entry -- 422 GROUNDING_FAILURE
    → persist ResumeVersion(json_payload) in REQUIRES_NEW tx       -- JSON always saved
    → LatexRenderer.render(templateId, json) → pdf_bytes
        · on timeout/failure: render_failed=true, keep JSON, surface 503/500 with resumeVersionId
    → returns ResumeResponse (201)
```

---

## LLM Tasks

### Task: ResumeTailoring

| Field | Value |
|---|---|
| Model Tier | TAILORING |
| LlmTask enum | `LlmTask.TAILORING` |
| Max input tokens | 6000 |
| Max output tokens | 2500 |

**Prompt outline:**
```
System: You produce ONLY structured resume JSON matching the provided schema. You may use ONLY the
        supplied experience entries verbatim in substance; never invent employers, titles, dates,
        metrics, or skills. No LaTeX, no prose outside JSON.
User:   Profile: {headline, summary, links}
        Target job: {sanitized JD title, company, responsibilities, skills}
        Candidate experience (the ONLY allowed source):
        {retrieved experience entries with ids}
        Return JSON conforming to the ResumeJsonPayload schema.
```

**Output JSON schema (ResumeJsonPayload — canonical schema lives in `.claude/skills/latex-resume-renderer.md`):**
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "candidateName": { "type": "string" },
    "contactInfo": { "type": "object" },
    "summary": { "type": "string" },
    "experience": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "sourceEntryId": { "type": "integer" },
          "title": { "type": "string" },
          "company": { "type": "string" },
          "startDate": { "type": "string" },
          "endDate": { "type": "string" },
          "bullets": { "type": "array", "items": { "type": "string" } }
        },
        "required": ["sourceEntryId", "title", "company", "bullets"]
      }
    },
    "skills": { "type": "array", "items": { "type": "string" } }
  },
  "required": ["candidateName", "experience", "skills"]
}
```
`sourceEntryId` on every experience item is what `GroundingValidator` checks against the retrieved set.

**Guardrails applied:**
- [x] PiiRedactionAdvisor
- [x] PromptInjectionAdvisor (JD is user-supplied)
- [x] GroundingValidator (resume tailoring)
- [x] JsonSchemaValidator

### Task: CoverLetterGeneration

| Field | Value |
|---|---|
| Model Tier | TAILORING |
| LlmTask enum | `LlmTask.COVER_LETTER` |
| Max input tokens | 5000 |
| Max output tokens | 1200 |

Same guardrail set; output is a `CoverLetterJsonPayload` (salutation, body paragraphs[], closing).
Grounding applies to any experience claims in the body.

---

## Rendering Tasks

### Templates Used

| Field | Value |
|---|---|
| Template IDs | `classic-ats`, `modern-twocol`, `compact`, `academic`, `cover-standard` |
| Template files | `src/main/resources/templates/latex/{id}.tex.mustache` |
| Registry | `TemplateRegistry` — adding a template = new `.tex.mustache` + registry entry |
| Engine | Mustache fill → Tectonic subprocess → PDF; configurable timeout; JSON is source of truth |

JSON input schema for the renderer is the `ResumeJsonPayload` above (see
`.claude/skills/latex-resume-renderer.md` for the authoritative field list).

---

## Test Plan

### Unit Tests

| Test class | What it tests |
|---|---|
| `TailoringServiceTest` | Orchestration with mocked LlmGateway/renderer; JSON persisted before render |
| `LatexRendererTest` | Valid JSON → non-empty PDF; render failure sets render_failed, keeps JSON |
| `TemplateRegistryTest` | Unknown template id → TemplateNotFoundException (400) |

### Integration Tests

| Test class | What it tests |
|---|---|
| `TailoringIntegrationTest` | Full tailor flow: seeded corpus → mocked LLM JSON → PDF stored; 201 |
| `TailoringPdfIntegrationTest` | `GET /resumes/{id}/pdf` returns application/pdf; re-render when bytes NULL |
| `TailoringOwnershipIntegrationTest` | 403 on another user's resume; 404 on missing |
| `CoverLetterIntegrationTest` | On-demand cover letter persisted + downloadable |

### Guardrail Tests

| Test | Scenario |
|---|---|
| `TailoringPiiRedactionTest` | PII stripped from prompt + logs before LLM call |
| `TailoringPromptInjectionTest` | Injected "ignore instructions" in JD is neutralized → 400 or sanitized |
| `TailoringGroundingTest` | LLM output referencing an unknown `sourceEntryId` → 422 GROUNDING_FAILURE |
| `TailoringSchemaValidationTest` | Malformed LLM JSON → one retry → 502 LLM_SCHEMA_ERROR |

`LlmGateway`/embeddings mocked in all tests; Tectonic runs for render tests (or is stubbed in CI if the
binary is unavailable — CI installs Tectonic).

---

## Deploy Steps

### Pre-deploy Checklist
- [ ] `./mvnw test` green; `./mvnw spotless:check` clean
- [ ] V8 + V9 migrations reviewed (additive)
- [ ] Tectonic installed in the Docker image; render smoke test passes in container
- [ ] Model tier config set (TAILORING = strong model; EXTRACTION = cheap)
- [ ] LLM provider key + token budget alerts configured; no secrets committed

### Environment Variables Required

| Variable | Description | Example |
|---|---|---|
| `OPENROUTER_API_KEY` | Prod LLM provider key | `{secret}` |
| `LLM_TAILORING_MODEL` | Strong model id for tailoring tier | `{model}` |
| `LLM_EXTRACTION_MODEL` | Cheap model id | `{model}` |
| `RETRIEVAL_TOP_K` | Experience entries retrieved | `6` |
| `TECTONIC_TIMEOUT_SECONDS` | Render subprocess timeout | `30` |

### DigitalOcean Deploy Steps
1. Push to `main`; green CI (must include Tectonic in the build/runtime image).
2. DO auto-deploys; Flyway applies V8 + V9.
3. Smoke: tailor a resume for a seeded JD as a test user; download the PDF; confirm token log row.

---

## Definition of Done

- [ ] Spec status set to APPROVED before implementation begins
- [ ] All tailoring + cover-letter endpoints implemented per schemas
- [ ] LLM emits JSON only; PDF rendering is deterministic and LLM-free
- [ ] All four guardrails execute inside `LlmGateway` and are individually tested
- [ ] JSON persisted even when render fails; PDF endpoint re-renders from JSON
- [ ] Grounding rejects invented experience (verified test); schema retry-then-502 verified
- [ ] V8 + V9 Flyway migrations apply cleanly
- [ ] All unit, integration, and guardrail tests pass (Testcontainers; LLM mocked)
- [ ] React tailoring UI: template picker, tailor action, history, PDF download
- [ ] `/review` checklist passes
- [ ] CLAUDE.md updated to Phase 4
- [ ] Deployed to DigitalOcean and smoke-tested
- [ ] Spec status set to IMPLEMENTED
