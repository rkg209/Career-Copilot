# Spec: Phase 2 — Candidate Profile & Experience Corpus

## Header

| Field | Value |
|---|---|
| Phase | 2 — Candidate Profile & Experience Corpus |
| Status | DRAFT |
| Version | 1.0 |
| Author | Rahul |
| Last Updated | 2026-07-01 |

> **Schema reconciliation note:** New tables use `BIGSERIAL` PKs; every `user_id` FK is `UUID` to match
> the deployed `users.id`. Confirm before APPROVED. See Phase 1 spec for rationale.

---

## Goal

Give each user a structured master profile plus a corpus of free-text experience entries, and embed each
entry into pgvector (private, per-user). This is the grounding corpus every downstream feature (tailoring,
scoring) retrieves from — nothing may be invented that is not here. This phase introduces the project's
first embedding calls through the same provider-agnostic gateway discipline, with asynchronous embedding
and change-detection so edits re-embed only when the underlying text actually changes. Outcome: a user
enters their real experience once and it becomes retrievable, semantically-searchable private data.

---

## Scope

### In Scope
- `candidate_profiles`: one-to-one with user; headline, summary, links, target roles/locations, remote flag.
- `experience_entries`: role, company, dates, description, achievements[], skills_used[], per-entry embedding.
- Profile CRUD (`GET`/`PUT /profile`) — created lazily on first PUT.
- Experience CRUD (`GET`/`POST`/`PUT`/`DELETE /profile/experience`), all owner-scoped.
- Embedding pipeline: on create/update, compute `text_hash`; if changed, embed async into `embedding`.
- `EmbeddingGateway` (mirrors the `LlmGateway` discipline): provider-agnostic, config-driven model/dim,
  token/latency logging to `token_usage_log`, PII redaction before the embedding call.
- Vector similarity query method `findTopKByVector(userId, queryVector, k)` (HNSW cosine) — the retrieval
  primitive Phase 3 will consume.
- React profile page: profile form + experience list with add/edit/delete.

### Out of Scope
- Using the corpus for tailoring or scoring (Phases 3/4 consume it; not built here).
- Resume/PDF generation, LLM chat generation (no `ChatClient` in this phase — embeddings only).
- File/resume upload parsing to auto-populate experience (manual entry only in v1).
- Cross-user or public exposure of any profile/experience data (strictly PRIVATE).

---

## Requirements Satisfied

| ID | Requirement |
|---|---|
| FR-2.1 | A user can create/update a structured profile (headline, summary, links, targets). |
| FR-2.2 | A user can add/edit/delete experience entries with role, company, dates, achievements. |
| FR-2.3 | Each experience entry is embedded into a per-user private vector for later retrieval. |
| FR-2.4 | Editing an entry re-embeds only when its text content changed (hash-based detection). |
| FR-2.5 | Top-k semantic retrieval over a user's own entries is available to internal callers. |
| NFR-2.1 | No user can read another user's profile or experience (owner-scoped + RLS). |
| NFR-2.2 | Embedding is asynchronous; the create/update API returns before embedding completes. |
| NFR-2.3 | Every embedding call logs `{task, model, tokens, latency}` to `token_usage_log`. |

---

## Data Model Changes

### New Tables

```sql
-- V5__candidate_profiles.sql
CREATE TABLE candidate_profiles (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             UUID            NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    headline            TEXT,
    summary             TEXT,
    location            TEXT,
    linkedin_url        TEXT,
    github_url          TEXT,
    portfolio_url       TEXT,
    target_roles        TEXT[]          NOT NULL DEFAULT '{}',
    target_locations    TEXT[]          NOT NULL DEFAULT '{}',
    open_to_remote      BOOLEAN         NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_candidate_profiles_user_id UNIQUE (user_id)
);
CREATE INDEX idx_candidate_profiles_user_id ON candidate_profiles (user_id);
```

```sql
-- V6__experience_entries.sql
-- Embedding dimension MUST match the configured embedding model (see appendix decision).
CREATE TABLE experience_entries (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             UUID            NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role                TEXT            NOT NULL,
    company             TEXT            NOT NULL,
    location            TEXT,
    start_date          DATE            NOT NULL,
    end_date            DATE,
    is_current          BOOLEAN         NOT NULL DEFAULT false,
    description         TEXT,
    achievements        TEXT[]          NOT NULL DEFAULT '{}',
    skills_used         TEXT[]          NOT NULL DEFAULT '{}',
    embedding           vector(1536),                       -- match text-embedding-3-small dim
    text_hash           CHAR(64),                           -- SHA-256 hex of embedded text
    embedded_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_experience_entries_dates   CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_experience_entries_current CHECK (NOT (is_current = true AND end_date IS NOT NULL))
);
CREATE INDEX idx_experience_entries_user_id     ON experience_entries (user_id);
CREATE INDEX idx_experience_entries_embedded_at ON experience_entries (embedded_at) WHERE embedded_at IS NULL;
CREATE INDEX idx_experience_entries_embedding_hnsw
    ON experience_entries USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
```

```sql
-- V7__token_usage_log.sql   (shared infra table; introduced here for embedding logging)
CREATE TYPE llm_task AS ENUM ('EXTRACTION','TAILORING','SCORING_AUGMENTATION','COVER_LETTER','EMBEDDING');
CREATE TABLE token_usage_log (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           UUID        REFERENCES users (id) ON DELETE SET NULL,
    task              llm_task    NOT NULL,
    model_name        TEXT        NOT NULL,
    prompt_tokens     INT         NOT NULL CHECK (prompt_tokens >= 0),
    completion_tokens INT         NOT NULL CHECK (completion_tokens >= 0),
    total_tokens      INT         NOT NULL GENERATED ALWAYS AS (prompt_tokens + completion_tokens) STORED,
    latency_ms        INT         CHECK (latency_ms >= 0),
    called_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_token_usage_log_user_id   ON token_usage_log (user_id);
CREATE INDEX idx_token_usage_log_called_at ON token_usage_log (called_at);
```

### Flyway Migrations Needed

| Migration | File | Description |
|---|---|---|
| V5 | `V5__candidate_profiles.sql` | `candidate_profiles` table |
| V6 | `V6__experience_entries.sql` | `experience_entries` + HNSW vector index |
| V7 | `V7__token_usage_log.sql` | `llm_task` enum + `token_usage_log` audit table |

---

## API Changes

### `GET /api/v1/profile` — OWNER_ONLY
**Response (200):** profile fields; if none exists yet, returns an empty profile shell (all nullable
fields null, arrays empty) — not a 404.

### `PUT /api/v1/profile` — OWNER_ONLY
Upsert (creates on first call).
**Request:**
```json
{
  "headline": "string | null",
  "summary": "string | null",
  "location": "string | null",
  "linkedinUrl": "string | null",
  "githubUrl": "string | null",
  "portfolioUrl": "string | null",
  "targetRoles": ["string"],
  "targetLocations": ["string"],
  "openToRemote": true
}
```
**Response (200):** the saved profile. **Errors:** `400 VALIDATION_FAILED`; `401`.

### `GET /api/v1/profile/experience` — OWNER_ONLY
**Response (200):** array of experience entries (ordered `start_date desc`), each including an
`embeddingStatus` field (`PENDING` | `EMBEDDED`) derived from `embedded_at`.

### `POST /api/v1/profile/experience` — OWNER_ONLY
**Request:**
```json
{
  "role": "string (required)",
  "company": "string (required)",
  "location": "string | null",
  "startDate": "YYYY-MM-DD (required)",
  "endDate": "YYYY-MM-DD | null",
  "isCurrent": false,
  "description": "string | null",
  "achievements": ["string"],
  "skillsUsed": ["string"]
}
```
**Response (201):** created entry with `embeddingStatus: "PENDING"`.
**Errors:** `400 VALIDATION_FAILED` (missing role/company/startDate, endDate < startDate, isCurrent with
endDate set); `401`.

### `PUT /api/v1/profile/experience/{id}` — OWNER_ONLY
Same body as POST. Re-embeds only if `text_hash` changes.
**Errors:** `400`; `403 ACCESS_DENIED` (entry belongs to another user); `404 RESOURCE_NOT_FOUND`.

### `DELETE /api/v1/profile/experience/{id}` — OWNER_ONLY
**Response (204).** **Errors:** `403 ACCESS_DENIED`; `404 RESOURCE_NOT_FOUND`.

---

## Service & Component Design

### Key Classes

| Class | Package | Responsibility |
|---|---|---|
| `ProfileController` | `com.careercopilot.profile` | Profile + experience endpoints |
| `ProfileService` | `com.careercopilot.profile` | Profile upsert; experience CRUD; hash + re-embed decision |
| `ExperienceEmbeddingService` | `com.careercopilot.profile` | Async `@Async` embed of changed entries |
| `CandidateProfileRepository` | `com.careercopilot.profile` | JPA repo (find/upsert by user) |
| `ExperienceRepository` | `com.careercopilot.profile` | JPA repo + `findTopKByVector(userId, vec, k)` (native HNSW) |
| `EmbeddingGateway` | `com.careercopilot.shared` | Provider-agnostic embed call; PII redaction; token logging |
| `TokenUsageLogRepository` | `com.careercopilot.shared` | Append-only usage audit writes |
| DTOs | `com.careercopilot.profile.dto` | `ProfileDto`, `ExperienceRequest`, `ExperienceResponse` |

### Interaction Flow

```
POST /profile/experience
  → ProfileController.addExperience(req, principal)
  → ProfileService.add(userId, req)
    → save entry (embedding NULL, embedded_at NULL)
    → publish ExperienceChangedEvent(entryId)
  → returns 201 (PENDING)   [embedding not yet done]

@Async on ExperienceChangedEvent:
  ExperienceEmbeddingService.embed(entryId)
    → load entry; text = role+company+description+achievements
    → hash = SHA-256(text); if hash == entry.text_hash → skip
    → EmbeddingGateway.embed(userId, EMBEDDING, text)   -- PII redaction + token log
    → entry.embedding = vec; entry.text_hash = hash; entry.embedded_at = now()

Internal retrieval (used by Phase 3):
  ExperienceRepository.findTopKByVector(userId, queryVector, k)
    → SELECT ... WHERE user_id = :userId ORDER BY embedding <=> :queryVector LIMIT :k
```

---

## LLM Tasks

No `ChatClient` generation tasks. This phase uses **embedding** calls only, routed through
`EmbeddingGateway` (same guardrail/logging discipline as `LlmGateway`):

| Field | Value |
|---|---|
| Task | Experience embedding |
| Tier | EXTRACTION-class (cheap embedding model) |
| `llm_task` enum | `EMBEDDING` |
| Model | config-driven (`spring.ai.*`), dimension must equal the `vector(N)` column |
| Guardrails | PiiRedactionAdvisor (strip PII before embedding + before logging text) |

There is no JSON schema / grounding / injection guard here (no free-form generation, no untrusted JD input).

---

## Rendering Tasks

N/A for this phase.

---

## Test Plan

### Unit Tests

| Test class | What it tests |
|---|---|
| `ProfileServiceTest` | Upsert profile; add/update experience; hash unchanged → no re-embed |
| `ExperienceEmbeddingServiceTest` | Changed text triggers embed; identical text skips; token log written |
| `EmbeddingGatewayTest` | PII stripped before embed call; usage logged with EMBEDDING task |

### Integration Tests

| Test class | What it tests |
|---|---|
| `ProfileIntegrationTest` | `GET`/`PUT /profile` owner-scoped; empty shell on first GET |
| `ExperienceIntegrationTest` | Full CRUD; 403 on other user's entry; 404 on missing |
| `ExperienceRetrievalIntegrationTest` | `findTopKByVector` returns only caller's entries, cosine-ordered |

`EmbeddingGateway` is mocked (`@MockBean`) in all tests — zero real embedding API calls.

### Guardrail Tests

| Test | Scenario |
|---|---|
| `EmbeddingPiiRedactionTest` | Confirms email/phone stripped from text before the embed call + before logging |

---

## Deploy Steps

### Pre-deploy Checklist
- [ ] `./mvnw test` green; `./mvnw spotless:check` clean
- [ ] V5–V7 migrations reviewed (additive)
- [ ] Embedding model + dimension confirmed to match `vector(N)` column
- [ ] Embedding provider key stored as DO secret

### Environment Variables Required

| Variable | Description | Example |
|---|---|---|
| `EMBEDDING_MODEL` | Embedding model id | `text-embedding-3-small` |
| `EMBEDDING_DIMENSION` | Must equal vector column dim | `1536` |
| `OPENROUTER_API_KEY` / `OLLAMA_BASE_URL` | Provider credentials (prod/dev) | `{secret}` |

### DigitalOcean Deploy Steps
1. Push to `main`; green CI.
2. DO auto-deploys; Flyway applies V5–V7.
3. Smoke: `PUT /profile` then `POST /profile/experience`; poll `GET /profile/experience` until
   `embeddingStatus: EMBEDDED`; confirm `token_usage_log` has an `EMBEDDING` row.

---

## Definition of Done

- [ ] Spec status set to APPROVED before implementation begins
- [ ] Profile + experience CRUD endpoints implemented per schemas, all owner-scoped
- [ ] Experience entries embed asynchronously; edit re-embeds only on text change
- [ ] `findTopKByVector` returns only the caller's entries in cosine order
- [ ] V5–V7 Flyway migrations apply cleanly
- [ ] Unit, integration, and PII guardrail tests pass with Testcontainers (LLM/embeddings mocked)
- [ ] React profile page performs full CRUD against the live API
- [ ] `/review` checklist passes
- [ ] CLAUDE.md updated to Phase 3
- [ ] Deployed to DigitalOcean and smoke-tested
- [ ] Spec status set to IMPLEMENTED
