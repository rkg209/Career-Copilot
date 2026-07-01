# Plan: Phase 2 — Candidate Profile & Experience Corpus

> Saved 2026-07-02. Status: plan complete, NOT yet started. Spec `specs/phase-2-profile-corpus.md` is still DRAFT — Step 1 amends and approves it before any code.

## Context

Phases 0–1 are implemented (JWT auth, job ingestion/portal, 39/39 tests green). Phase 2 gives each user a structured master profile plus free-text experience entries, each embedded into pgvector (private per user). This corpus is the grounding source for Phase 3 (tailoring) and Phase 4 (scoring) — nothing downstream may invent experience not stored here.

A detailed **DRAFT spec already exists: `specs/phase-2-profile-corpus.md`**. Per the project's SDD loop, implementation happens strictly against an APPROVED spec. This plan = (a) amend the spec to match reality, (b) mark it APPROVED, (c) implement it.

**Decision made with user (2026-07-02):** embeddings via **Ollama only — `nomic-embed-text`, 768 dimensions**, dev and prod. Prod (DO App Platform) has no Ollama today, so embedding must degrade gracefully: CRUD always succeeds; entries stay `embeddingStatus: PENDING` until an Ollama endpoint (`OLLAMA_BASE_URL`) is reachable. A retry sweep picks up un-embedded entries.

Gaps found between spec and codebase (must fix in Step 1):
1. Spec plans migrations V5–V7 but `V5__add_ats_job_sources.sql` already exists → renumber to **V6–V8**.
2. Spring AI is not yet in `pom.xml` — Phase 2 introduces it (embedding only, no ChatClient).
3. Spec assumed `text-embedding-3-small` / vector(1536) → change to `nomic-embed-text` / **vector(768)**.

## Step 1 — Amend spec, set APPROVED

Edit `specs/phase-2-profile-corpus.md`:
- Migrations renumber V5–V7 → V6–V8.
- `vector(1536)` → `vector(768)`; model = `nomic-embed-text` via Ollama (Spring AI Ollama starter), config-driven.
- Env vars: drop `OPENROUTER_API_KEY` for this phase; keep `OLLAMA_BASE_URL`, `EMBEDDING_MODEL=nomic-embed-text`, `EMBEDDING_DIMENSION=768`.
- Add graceful-degradation note (prod without Ollama → PENDING entries, retry sweep, no CRUD failures).
- Confirm the UUID reconciliation note (verified: `users.id` is UUID) and flip Status → **APPROVED**.

## Step 2 — Migrations (V6–V8)

New files in `src/main/resources/db/migration/` (schemas exactly as in the spec, with the two fixes above):
- `V6__candidate_profiles.sql` — 1:1 with user (`user_id UUID` FK, unique), headline/summary/links/target arrays.
- `V7__experience_entries.sql` — role/company/dates/description/achievements[]/skills_used[], `embedding vector(768)`, `text_hash CHAR(64)`, `embedded_at`, HNSW cosine index (m=16, ef_construction=64), partial index on `embedded_at IS NULL`, date CHECK constraints.
- `V8__token_usage_log.sql` — `llm_task` native enum (`EXTRACTION, TAILORING, SCORING_AUGMENTATION, COVER_LETTER, EMBEDDING`) + append-only usage table. Enum mapped with `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` per project convention.

## Step 3 — Backend: `com.careercopilot.profile` + shared embedding infra

**Dependencies (`pom.xml`):** Spring AI BOM 1.1.x + `spring-ai-starter-model-ollama` (embedding auto-config only; **no ChatClient this phase**). Configure in `application.yml`: `spring.ai.ollama.base-url` + embedding model/dim, chat autoconfiguration excluded/disabled.

**Shared (`com.careercopilot.shared`):**
- `EmbeddingGateway` — wraps Spring AI `EmbeddingModel`; provider-agnostic; PII redaction via new `PiiRedactionService` (regex strip emails/phones before the embed call and before any logging); logs `{task=EMBEDDING, model, tokens, latency}` to `token_usage_log`; throws a typed exception on provider unavailability (callers treat as "stay PENDING").
- `TokenUsageLog` entity + `TokenUsageLogRepository`, `LlmTask` enum.

**Profile feature slice (mirror the `jobs` slice layout — `JobController`/`JobService`/`JobPostingMapper` as the pattern):**
- `CandidateProfile`, `ExperienceEntry` entities; `CandidateProfileRepository`, `ExperienceRepository` (native query `findTopKByVector(userId, vector, k)` — `WHERE user_id = :userId ORDER BY embedding <=> :vec LIMIT :k`; every query filters by userId).
- `ProfileService` — profile upsert (lazy create on first PUT; GET returns empty shell, not 404); experience CRUD, owner-scoped (403 on foreign entry, 404 on missing — reuse `ResourceNotFoundException` / add access-denied handling in `GlobalExceptionHandler`).
- `ExperienceEmbeddingService` — listens to `ExperienceChangedEvent` (`@Async` + `@EnableAsync` config); builds canonical text (role+company+description+achievements), SHA-256 hash; skips if hash unchanged; on success sets `embedding`, `text_hash`, `embedded_at`. Plus a `@Scheduled` retry sweep over `embedded_at IS NULL` rows (handles prod-without-Ollama and transient failures).
- `ProfileController` — endpoints per spec under `/api/v1/profile` and `/api/v1/profile/experience`, all authenticated (register in `SecurityConfig`), DTOs only (`ProfileDto`, `ExperienceRequest`, `ExperienceResponse` with derived `embeddingStatus: PENDING|EMBEDDED`), Bean Validation for required fields/date rules.

## Step 4 — Frontend

- `frontend/src/pages/ProfilePage.tsx` (route `/profile`, authenticated): profile form (headline, summary, links, target roles/locations chips, remote toggle) + experience list with add/edit/delete form, `embeddingStatus` badge.
- Extend `frontend/src/api.ts` with `getProfile`, `putProfile`, `listExperience`, `addExperience`, `updateExperience`, `deleteExperience` (JWT header, same style as existing job functions).
- Add nav link to `/profile` for logged-in users in `App.tsx`.

## Step 5 — Tests (Testcontainers `pgvector/pgvector:pg16`, EmbeddingGateway mocked — zero real API calls)

Per spec test plan:
- Unit: `ProfileServiceTest`, `ExperienceEmbeddingServiceTest` (changed text embeds / identical text skips / token log written), `EmbeddingGatewayTest` (PII stripped, usage logged).
- Integration: `ProfileIntegrationTest` (GET/PUT owner-scoped, empty shell), `ExperienceIntegrationTest` (CRUD, 403 cross-user, 404), `ExperienceRetrievalIntegrationTest` (findTopKByVector returns only caller's entries, cosine-ordered — insert vectors directly in test).
- Guardrail: `EmbeddingPiiRedactionTest`.

## Verification

1. `./mvnw test` — full suite green (existing 39+ plus new Phase 2 tests).
2. Run `/review` against CLAUDE.md rules (privacy: every profile/experience query userId-filtered; no entities at controller boundary; embedding only via EmbeddingGateway).
3. Manual smoke with local Ollama (`ollama pull nomic-embed-text`): register/login → PUT profile → POST experience → poll GET until `embeddingStatus: EMBEDDED` → confirm `token_usage_log` row with task `EMBEDDING`; verify user B gets 403/404 on user A's entry.
4. Frontend: `npm run dev`, exercise profile page CRUD end-to-end.
5. Update CLAUDE.md current phase; mark spec IMPLEMENTED. DigitalOcean redeploy (note: Phase 1 deploy checkpoint is also still pending — can ship together; prod embeddings stay PENDING until `OLLAMA_BASE_URL` points at a reachable Ollama).

## Key files

- Spec: `specs/phase-2-profile-corpus.md` (amend → APPROVED)
- Migrations: `src/main/resources/db/migration/V6..V8__*.sql`
- New: `src/main/java/com/careercopilot/profile/**`, `shared/EmbeddingGateway.java`, `shared/PiiRedactionService.java`, `shared/TokenUsageLog*.java`
- Touch: `pom.xml`, `application.yml`, `shared/SecurityConfig.java`, `shared/GlobalExceptionHandler.java`, `frontend/src/api.ts`, `frontend/src/App.tsx`, new `frontend/src/pages/ProfilePage.tsx`
- Reference: `.claude/skills/spring-ai-rag.md` (gateway/tier discipline), `jobs/` slice as the structural template
