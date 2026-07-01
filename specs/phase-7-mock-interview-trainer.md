# Spec: Phase 7 — Mock-Interview Trainer + Spaced Repetition

## Header

| Field | Value |
|---|---|
| Phase | 7 — Mock-Interview Trainer + Spaced Repetition |
| Status | DRAFT |
| Version | 1.0 |
| Author | Rahul |
| Last Updated | 2026-07-02 |

> **Schema reconciliation note:** These tables are new (not in the canonical baseline schema) — designed
> here. `BIGSERIAL` PKs; `user_id` FKs are `UUID`. Confirm before APPROVED.

---

## Goal

Turn captured interview data into active practice. This phase generates interview questions for a user
from three sources — their own interview logs (Phase 5), the public company-intel pool (Phase 6), and
role/skill patterns — evaluates the user's typed answers with an LLM rubric, and schedules weak areas
for review using the SM-2 spaced-repetition algorithm. Each answered question becomes a review card;
SM-2 computes the next due date from the self/LLM-graded quality. Outcome: the user practices the
questions most likely to appear, and the system keeps resurfacing the ones they are weakest on.

---

## Scope

### In Scope
- Question generation: `EXTRACTION`/`TAILORING`-tier LLM task producing questions from
  {user's logs + public intel for target company/role + role-pattern seeds}.
- `mock_questions`: generated/curated question bank (per user; may reference a company/role).
- Practice session: serve a batch of due + new questions; accept typed answers.
- Answer evaluation: LLM rubric scoring (0–5 quality + feedback) — `SCORING_AUGMENTATION` tier.
- SM-2 scheduler: per-question review card with `ease_factor`, `interval_days`, `repetitions`,
  `due_at`; standard SM-2 update from quality grade.
- `GET /review/due` queue; endpoints to generate, answer/grade, and fetch due cards.
- React: practice page (question → answer box → feedback + grade), a "due today" review queue.

### Out of Scope
- Voice/audio interviews or timed live simulation (text only in v1).
- Coding-execution grading (rubric evaluation is textual, not compile-and-run).
- Sharing practice results to the public pool (practice data is private).
- Multi-user leaderboards.

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-7.1 | The system generates practice questions from the user's logs, public intel, and role patterns. |
| FR-7.2 | The user answers a question and receives an LLM rubric score (0–5) with feedback. |
| FR-7.3 | Each graded question is scheduled for review via SM-2 (ease, interval, due date). |
| FR-7.4 | The user can fetch the set of questions due for review today. |
| NFR-7.1 | SM-2 scheduling is deterministic given the grade sequence (unit-testable). |
| NFR-7.2 | Generation grounds on the user's own data + published pool only; no invented company facts. |
| NFR-7.3 | All practice data is owner-scoped and private. |

---

## Data Model Changes

### New Tables

```sql
-- V14__mock_questions.sql
CREATE TABLE mock_questions (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company        TEXT,                                   -- optional target company
    role_category  TEXT,
    round_type     interview_round_type,                  -- reuse Phase 5 enum
    question_text  TEXT        NOT NULL,
    source         TEXT        NOT NULL,                   -- 'OWN_LOG' | 'PUBLIC_INTEL' | 'ROLE_PATTERN'
    source_ref_id  BIGINT,                                 -- optional origin (interview_log / intel id)
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mock_questions_user_id ON mock_questions (user_id);
CREATE INDEX idx_mock_questions_user_company ON mock_questions (user_id, company);
```

```sql
-- V15__practice_answers.sql
CREATE TABLE practice_answers (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mock_question_id  BIGINT      NOT NULL REFERENCES mock_questions (id) ON DELETE CASCADE,
    answer_text       TEXT        NOT NULL,
    quality_grade     SMALLINT    CHECK (quality_grade BETWEEN 0 AND 5),
    feedback          TEXT,
    llm_model         TEXT,
    token_count       INT         CHECK (token_count >= 0),
    answered_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_practice_answers_user_id  ON practice_answers (user_id);
CREATE INDEX idx_practice_answers_question ON practice_answers (mock_question_id);
```

```sql
-- V16__review_cards.sql   (SM-2 state, one card per user+question)
CREATE TABLE review_cards (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mock_question_id  BIGINT      NOT NULL REFERENCES mock_questions (id) ON DELETE CASCADE,
    ease_factor       NUMERIC(4,2) NOT NULL DEFAULT 2.50,   -- SM-2 EF, min 1.30
    interval_days     INT         NOT NULL DEFAULT 0,
    repetitions       INT         NOT NULL DEFAULT 0,
    last_grade        SMALLINT    CHECK (last_grade BETWEEN 0 AND 5),
    due_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_review_cards_user_question UNIQUE (user_id, mock_question_id)
);
CREATE INDEX idx_review_cards_due ON review_cards (user_id, due_at);
```

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V14 | `V14__mock_questions.sql` | Generated question bank |
| V15 | `V15__practice_answers.sql` | Answers + rubric grades |
| V16 | `V16__review_cards.sql` | SM-2 spaced-repetition state |

---

## API Changes

### `POST /api/v1/trainer/generate` — OWNER_ONLY
**Request:**
```json
{ "company": "Acme Corp", "roleCategory": "Software Engineer", "roundType": "TECHNICAL", "count": 10 }
```
All optional except `count` (1–25). **Response (201):** array of created `mock_questions`.
**Errors:** `400 VALIDATION_FAILED`; `401`; `422 GROUNDING_FAILURE` (LLM invented company facts);
`502 LLM_SCHEMA_ERROR`; `503 LLM_UNAVAILABLE`.

### `POST /api/v1/trainer/questions/{id}/answer` — OWNER_ONLY
**Request:** `{ "answerText": "string (required)" }`
**Response (201):**
```json
{
  "practiceAnswerId": 55,
  "mockQuestionId": 12,
  "qualityGrade": 3,
  "feedback": "Good structure; missing complexity analysis.",
  "nextReview": { "dueAt": "2026-07-08T00:00:00Z", "intervalDays": 6, "easeFactor": 2.46 }
}
```
Grading updates/creates the SM-2 `review_card`. **Errors:** `400`; `403 ACCESS_DENIED`; `404`;
`502 LLM_SCHEMA_ERROR`; `503 LLM_UNAVAILABLE`.

### `GET /api/v1/trainer/review/due` — OWNER_ONLY
Query: `limit` (default 20). Returns cards with `due_at <= now()` (weakest/oldest first) joined to
their question text. **Response (200):** `{ "content": [ { "mockQuestionId": 12, "questionText": "…",
"dueAt": "…", "repetitions": 2 } ] }`. **Errors:** `401`.

### `GET /api/v1/trainer/questions` — OWNER_ONLY
Paginated list of the user's generated questions with last grade (if any). **Errors:** `401`.

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `TrainerController` | `com.careercopilot.trainer` | Generate, answer, due-queue, list endpoints |
| `QuestionGenerationService` | `com.careercopilot.trainer` | Assemble sources → LLM → persist questions |
| `AnswerEvaluationService` | `com.careercopilot.trainer` | LLM rubric grade + feedback; trigger scheduler |
| `Sm2Scheduler` | `com.careercopilot.trainer` | Pure SM-2 update of a review card from a grade |
| `MockQuestionRepository` / `PracticeAnswerRepository` / `ReviewCardRepository` | `com.careercopilot.trainer` | Persistence |
| DTOs | `com.careercopilot.trainer.dto` | `GenerateRequest`, `AnswerRequest`, `AnswerResult`, `DueCard` |

### Interaction Flow

```
POST /trainer/generate
  → TrainerController.generate(req, principal)
  → QuestionGenerationService.generate(userId, req)
    → gather: user's interview_logs (own) + published company_interview_intel + role-pattern seeds
    → LlmGateway.call(TAILORING, prompt{sources})   -- JSON list; grounding limits company claims
    → persist mock_questions (source tagged)
  → returns 201

POST /trainer/questions/{id}/answer
  → AnswerEvaluationService.evaluate(userId, questionId, answerText)
    → owner check (403/404)
    → LlmGateway.call(SCORING_AUGMENTATION, rubric prompt) → {grade 0-5, feedback}
    → persist practice_answer
    → Sm2Scheduler.update(reviewCard, grade):
        · grade < 3 → repetitions=0, interval=1
        · else repetitions++, interval = 1 → 6 → round(prev*EF)
        · EF' = max(1.30, EF + (0.1 - (5-grade)*(0.08 + (5-grade)*0.02)))
        · due_at = now() + interval days
  → returns AnswerResult (201)
```

---

## LLM Tasks

### Task: QuestionGeneration

| Field | Value |
|---|---|
| Model Tier | TAILORING |
| LlmTask enum | `LlmTask.TAILORING` |
| Max input tokens | 4000 |
| Max output tokens | 1500 |

**Prompt outline:**
```
System: Generate realistic interview questions from the supplied sources ONLY. Do not invent facts
        about the company beyond the supplied intel. Return a JSON array of question strings.
User:   Company/role/round: {…}
        User's past interview logs: {…}
        Public intel for this company: {published intel}
        Role-pattern seeds: {…}
```

**Output JSON schema:**
```json
{ "type": "object",
  "properties": { "questions": { "type": "array", "items": { "type": "string" } } },
  "required": ["questions"] }
```

**Guardrails:** [x] PiiRedactionAdvisor · [x] GroundingValidator (no invented company facts) ·
[x] JsonSchemaValidator · PromptInjection N/A (inputs are own/published data).

### Task: AnswerEvaluation

| Field | Value |
|---|---|
| Model Tier | SCORING_AUGMENTATION |
| LlmTask enum | `LlmTask.SCORING_AUGMENTATION` |
| Max input tokens | 2500 |
| Max output tokens | 500 |

**Output JSON schema:**
```json
{ "type": "object",
  "properties": { "grade": { "type": "integer", "minimum": 0, "maximum": 5 }, "feedback": { "type": "string" } },
  "required": ["grade", "feedback"] }
```
**Guardrails:** [x] PiiRedactionAdvisor · [x] JsonSchemaValidator.

---

## Rendering Tasks

N/A for this phase.

---

## Test Plan

### Unit Tests

| Test class | What it tests |
|---|---|
| `Sm2SchedulerTest` | Grade sequences produce exact EF/interval/due per SM-2; EF floor 1.30; lapse resets |
| `QuestionGenerationServiceTest` | Sources assembled; grounded output persisted; injection-free |
| `AnswerEvaluationServiceTest` | Rubric JSON parsed; scheduler invoked with the grade |

### Integration Tests

| Test class | What it tests |
|---|---|
| `TrainerGenerateIntegrationTest` | Generate persists questions (mocked LLM); owner-scoped |
| `TrainerAnswerIntegrationTest` | Answer grades + creates/updates a review card; due date advances |
| `TrainerDueQueueIntegrationTest` | `GET /review/due` returns only cards due for the caller |

### Guardrail Tests

| Test | Scenario |
|---|---|
| `TrainerGroundingTest` | Generated question referencing an un-supplied company fact → 422 |
| `TrainerSchemaValidationTest` | Malformed rubric JSON → retry → 502 |

`LlmGateway` mocked in all tests.

---

## Deploy Steps

### Pre-deploy Checklist
- [ ] `./mvnw test` green; `./mvnw spotless:check` clean
- [ ] V14–V16 migrations reviewed (additive)
- [ ] SM-2 constants (initial EF, intervals) confirmed in `Sm2Scheduler`
- [ ] Model tiers set for generation + evaluation

### Environment Variables Required

| Variable | Description | Example |
|---|---|---|
| `TRAINER_DAILY_NEW_LIMIT` | Max new cards introduced per day | `20` |

### DigitalOcean Deploy Steps
1. Push to `main`; green CI.
2. DO auto-deploys; Flyway applies V14–V16.
3. Smoke: generate questions for a company, answer one, confirm grade + `nextReview`, then `GET /review/due`.

---

## Definition of Done

- [ ] Spec status set to APPROVED before implementation begins
- [ ] Generate, answer, and due-queue endpoints implemented per schemas
- [ ] SM-2 scheduling is deterministic and unit-verified against known grade sequences
- [ ] Question generation grounds only on user logs + published intel + role seeds
- [ ] Answers graded 0–5 with feedback; review cards created/updated
- [ ] V14–V16 Flyway migrations apply cleanly
- [ ] Unit, integration, and guardrail tests pass (Testcontainers; LLM mocked)
- [ ] React practice + due-review UI work against the live API
- [ ] `/review` checklist passes
- [ ] CLAUDE.md updated to Phase 8
- [ ] Deployed to DigitalOcean and smoke-tested
- [ ] Spec status set to IMPLEMENTED
