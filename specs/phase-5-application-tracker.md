# Spec: Phase 5 — Application & Interview Tracker

## Header

| Field | Value |
|---|---|
| Phase | 5 — Application & Interview Tracker |
| Status | DRAFT |
| Version | 1.0 |
| Author | Rahul |
| Last Updated | 2026-07-02 |

> **Schema reconciliation note:** New tables use `BIGSERIAL` PKs; `user_id` FKs are `UUID`. Confirm
> before APPROVED.

---

## Goal

Give each user one place to record every application and interview. This phase adds a per-user
application lifecycle (a state machine over `SAVED → APPLIED → … → OFFER/REJECTED/WITHDRAWN`) with
dates, notes, and optional links to generated resume/cover-letter documents, plus structured interview
logging (round type, questions asked, process notes, outcome, difficulty) attached to an application.
Capture only — the mock-interview trainer (Phase 7) and the public anonymized pool (Phase 6) consume
this data later. Outcome: nothing about a job search is lost; rejections become future prep material.

---

## Scope

### In Scope
- `applications`: user-scoped, with denormalized job title/company snapshot (survives job deletion),
  optional links to `resume_versions`/`cover_letters`, status, applied date, notes, source url.
- Application status state machine with validated transitions (invalid transition → 400).
- `interview_logs`: attached to an application; round type/number, date, duration, interviewer role
  (title only, no name), questions asked[], notes, feedback, outcome, difficulty.
- Endpoints: list/create/update applications; list/create interview logs; a per-log **contribute**
  endpoint stub that Phase 6 fully implements (returns `202`/staged placeholder here, or is deferred).
- React: kanban/list of applications by status, application detail with interview-log entry form.

### Out of Scope
- Anonymization + public pooling of interview logs (Phase 6 — `contribute` is only stubbed here).
- Mock-interview generation / spaced repetition (Phase 7).
- Auto-creating an application when a resume is tailored (manual "track this" action only in v1).
- Email/calendar reminders for interview dates (v2).

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-5.1 | A user can create an application from a job posting (or manually) and track its status. |
| FR-5.2 | Application status transitions are validated against a defined state machine. |
| FR-5.3 | Title/company are snapshotted so an application survives deletion of the job posting. |
| FR-5.4 | A user can attach interview logs (round, questions, notes, outcome) to an application. |
| FR-5.5 | Optional links associate an application with a generated resume and/or cover letter. |
| NFR-5.1 | All application/interview data is strictly owner-scoped (no cross-user access). |
| NFR-5.2 | Interview logs never store an interviewer's personal name (role title only). |

---

## Data Model Changes

### New Tables

```sql
-- V11__applications.sql
CREATE TYPE application_status AS ENUM (
    'SAVED','APPLIED','PHONE_SCREEN','TECHNICAL_INTERVIEW','FINAL_ROUND','OFFER','REJECTED','WITHDRAWN'
);

CREATE TABLE applications (
    id                 BIGSERIAL          PRIMARY KEY,
    user_id            UUID               NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_posting_id     BIGINT             REFERENCES job_postings (id) ON DELETE SET NULL,
    job_title_snapshot TEXT               NOT NULL,
    company_snapshot   TEXT               NOT NULL,
    resume_version_id  BIGINT             REFERENCES resume_versions (id) ON DELETE SET NULL,
    cover_letter_id    BIGINT             REFERENCES cover_letters (id) ON DELETE SET NULL,
    status             application_status NOT NULL DEFAULT 'SAVED',
    applied_at         TIMESTAMPTZ,
    notes              TEXT,
    source_url         TEXT,
    created_at         TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ        NOT NULL DEFAULT now()
);
CREATE INDEX idx_applications_user_id        ON applications (user_id);
CREATE INDEX idx_applications_status         ON applications (user_id, status);
CREATE INDEX idx_applications_job_posting_id ON applications (job_posting_id);
CREATE INDEX idx_applications_applied_at     ON applications (user_id, applied_at DESC);
```

```sql
-- V12__interview_logs.sql
CREATE TYPE interview_round_type AS ENUM (
    'PHONE_SCREEN','TECHNICAL','SYSTEM_DESIGN','BEHAVIOURAL','TAKE_HOME','PANEL','HR','OTHER'
);
CREATE TYPE contribution_status AS ENUM ('STAGED','PUBLISHED');

CREATE TABLE interview_logs (
    id                  BIGSERIAL            PRIMARY KEY,
    application_id      BIGINT               NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    user_id             UUID                 NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    round_type          interview_round_type NOT NULL,
    round_number        SMALLINT             NOT NULL DEFAULT 1 CHECK (round_number >= 1),
    interview_date      DATE,
    duration_minutes    SMALLINT             CHECK (duration_minutes > 0),
    interviewer_role    TEXT,
    questions_asked     TEXT[]               NOT NULL DEFAULT '{}',
    notes               TEXT,
    feedback_received   TEXT,
    outcome             TEXT,
    difficulty          SMALLINT             CHECK (difficulty BETWEEN 1 AND 5),
    contributed_at      TIMESTAMPTZ,
    contribution_status contribution_status,
    created_at          TIMESTAMPTZ          NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ          NOT NULL DEFAULT now()
);
CREATE INDEX idx_interview_logs_application_id ON interview_logs (application_id);
CREATE INDEX idx_interview_logs_user_id        ON interview_logs (user_id);
CREATE INDEX idx_interview_logs_contributed_at ON interview_logs (contributed_at) WHERE contributed_at IS NULL;
```

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V11 | `V11__applications.sql` | `application_status` enum + `applications` table |
| V12 | `V12__interview_logs.sql` | round/contribution enums + `interview_logs` table |

### Status State Machine

```
SAVED → APPLIED → PHONE_SCREEN → TECHNICAL_INTERVIEW → FINAL_ROUND → OFFER
Any non-terminal state → REJECTED | WITHDRAWN
Terminal: OFFER, REJECTED, WITHDRAWN (no outbound transitions)
```
Any transition not on this graph → `400 INVALID_STATUS_TRANSITION`.

---

## API Changes

### `GET /api/v1/tracker/applications` — OWNER_ONLY
Paginated (`sort`: `createdAt`,`updatedAt`,`appliedAt`,`status`,`companyName`; default `updatedAt,desc`).
Returns application summaries.

### `POST /api/v1/tracker/applications` — OWNER_ONLY
**Request:**
```json
{
  "jobPostingId": 42,
  "jobTitleSnapshot": "Backend Engineer",
  "companySnapshot": "Acme Corp",
  "resumeVersionId": 17,
  "coverLetterId": null,
  "status": "SAVED",
  "appliedAt": null,
  "notes": "string | null",
  "sourceUrl": "string | null"
}
```
If `jobPostingId` is provided, snapshots default from the posting when omitted. **Response (201):**
the created application. **Errors:** `400 VALIDATION_FAILED`; `400 INVALID_STATUS_TRANSITION`
(illegal initial status); `401`; `404 RESOURCE_NOT_FOUND` (linked job/resume not found).

### `PUT /api/v1/tracker/applications/{id}` — OWNER_ONLY
Updates status/notes/appliedAt/links. Status changes validated against the state machine.
**Errors:** `400 VALIDATION_FAILED`; `400 INVALID_STATUS_TRANSITION`; `403 ACCESS_DENIED`; `404`.

### `GET /api/v1/tracker/applications/{id}/interview-logs` — OWNER_ONLY
Returns interview logs for the application (ordered `round_number asc`). **Errors:** `403`; `404`.

### `POST /api/v1/tracker/applications/{id}/interview-logs` — OWNER_ONLY
**Request:**
```json
{
  "roundType": "TECHNICAL",
  "roundNumber": 2,
  "interviewDate": "2026-06-30",
  "durationMinutes": 60,
  "interviewerRole": "Senior Engineer",
  "questionsAsked": ["Reverse a linked list", "Design a rate limiter"],
  "notes": "string | null",
  "feedbackReceived": "string | null",
  "outcome": "PASSED | FAILED | PENDING | UNKNOWN",
  "difficulty": 4
}
```
**Response (201):** created log. **Errors:** `400 VALIDATION_FAILED` (difficulty out of 1–5,
duration ≤ 0); `403 ACCESS_DENIED`; `404` (application not found).

### `POST /api/v1/tracker/applications/{id}/interview-logs/{logId}/contribute` — OWNER_ONLY
**Phase 5 stub:** validates ownership, marks intent, returns `202 { "status": "PENDING" }`.
Full anonymize-and-stage/publish behavior is implemented in Phase 6.
**Errors:** `403`; `404`; `409 ALREADY_CONTRIBUTED`.

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `TrackerController` | `com.careercopilot.tracker` | Application + interview-log endpoints |
| `ApplicationService` | `com.careercopilot.tracker` | CRUD + status state-machine validation |
| `InterviewLogService` | `com.careercopilot.tracker` | Interview-log CRUD; contribute stub |
| `ApplicationStatusMachine` | `com.careercopilot.tracker` | Allowed-transition table + validation |
| `ApplicationRepository` / `InterviewLogRepository` | `com.careercopilot.tracker` | Persistence |
| DTOs | `com.careercopilot.tracker.dto` | `ApplicationRequest/Response`, `InterviewLogRequest/Response` |

### Interaction Flow

```
PUT /tracker/applications/{id}   (status change)
  → TrackerController.update(id, req, principal)
  → ApplicationService.update(userId, id, req)
    → load application (owner check → 403 / 404)
    → ApplicationStatusMachine.assertAllowed(current, requested)   -- 400 INVALID_STATUS_TRANSITION
    → apply changes; if →APPLIED and applied_at null, set applied_at = now()
  → returns ApplicationResponse

POST /tracker/applications/{id}/interview-logs
  → InterviewLogService.add(userId, applicationId, req)
    → verify application ownership
    → save interview_log (contributed_at NULL)
  → returns InterviewLogResponse (201)
```

---

## LLM Tasks

N/A for this phase — pure CRUD + state machine. No LLM or embedding calls.

---

## Rendering Tasks

N/A for this phase.

---

## Test Plan

### Unit Tests

| Test class | What it tests |
|---|---|
| `ApplicationStatusMachineTest` | Every legal transition allowed; every illegal one rejected |
| `ApplicationServiceTest` | Snapshot defaulting from posting; applied_at auto-set on →APPLIED |
| `InterviewLogServiceTest` | Ownership enforced; difficulty/duration validation |

### Integration Tests

| Test class | What it tests |
|---|---|
| `ApplicationTrackerIntegrationTest` | Create/list/update; pagination + sort; 400 on bad transition |
| `ApplicationOwnershipIntegrationTest` | 403 on another user's application; snapshot survives job delete |
| `InterviewLogIntegrationTest` | Add/list logs; 403/404 cases; contribute stub returns 202 |

### Guardrail Tests

N/A (no LLM tasks this phase).

---

## Deploy Steps

### Pre-deploy Checklist
- [ ] `./mvnw test` green; `./mvnw spotless:check` clean
- [ ] V11 + V12 migrations reviewed (additive; enums created)
- [ ] State-machine transition table reviewed
- [ ] No secrets committed

### Environment Variables Required

None new for this phase.

### DigitalOcean Deploy Steps
1. Push to `main`; green CI.
2. DO auto-deploys; Flyway applies V11 + V12.
3. Smoke: create an application from a job, advance its status, add an interview log, list them back.

---

## Definition of Done

- [ ] Spec status set to APPROVED before implementation begins
- [ ] Application CRUD + interview-log endpoints implemented per schemas, owner-scoped
- [ ] Status transitions validated by the state machine; illegal transitions return 400
- [ ] Title/company snapshotted; application survives deletion of its job posting
- [ ] `contribute` stub returns 202 and is ready for Phase 6 to complete
- [ ] V11 + V12 Flyway migrations apply cleanly
- [ ] Unit + integration tests pass with Testcontainers
- [ ] React tracker board + interview-log form work against the live API
- [ ] `/review` checklist passes
- [ ] CLAUDE.md updated to Phase 6
- [ ] Deployed to DigitalOcean and smoke-tested
- [ ] Spec status set to IMPLEMENTED
