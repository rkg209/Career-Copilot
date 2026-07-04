# Career Copilot — Progress Report

> **As of:** 2026-07-05 · **Branch:** `main` · **Tests:** 173/173 green (139 unit verified in-session; 34 new unit tests for Phase 5; 6 new Testcontainers integration tests pending a Docker-available run) · **Current phase:** Phase 6 — Company Interview Intel (next up)
>
> This report tracks the project sequentially: from the planning folder and the master document (`career-copilot.md`), through the Claude Code spec-driven-development (SDD) setup, and phase by phase through Phase 4. Every stage records what was done, the decisions taken, and the issues resolved.

---

## Snapshot — Where the Project Stands Today

| Item | State |
|---|---|
| Phases complete (code + tests) | 0, 1, 2, 3, 4, 5 |
| Deployed to DigitalOcean | Phase 0 only (deploy checkpoint 0) |
| **Deploy checkpoint 1** | **PENDING — now covers Phases 1–5 stacked together** |
| Test suite | 173/173 (139 unit confirmed green this session; 34 new Phase 5 unit tests confirmed green; 6 new Testcontainers integration tests need a Docker-available run to confirm) |
| Specs | Phases 0–5 written & implemented; Phases 6–8 specs drafted |
| Headline metric artifact | `AtsEvalHarnessTest` + 20-JD labeled set (deterministic, LLM-free) |

---

## Stage 1 — Planning Folder (`planning/`)

The project began as a set of planning documents that fixed the requirements, architecture, and data model before any tooling or code existed:

| Document | What it established |
|---|---|
| `01-project-summary.md` | Executive summary: multi-user job-search copilot; headline metric = before/after ATS score improvement on a hand-labeled 20-JD set |
| `01-requirements.md` | Functional + non-functional requirements (≥100 users, per-user privacy, ~$25/month LLM budget) |
| `02-architecture.md` | Layered, package-by-feature Spring Boot architecture; provider-agnostic LLM access |
| `03-system-design.md` | End-to-end pipelines (ingestion, RAG tailoring, ATS scoring, intel pooling) |
| `04-database-design.md` + `04-database-schema.sql` | Full relational + pgvector schema with the private/shared privacy split |
| `05-api-design.md` + `05-openapi.yaml` | REST API surface, versioned under `/api/v1` |

Key outcome of this stage: the **two-framings-one-codebase** idea — the same system presented as an AI/ML application (RAG, pgvector, guardrails, evaluation) and as an SDE/full-stack product (REST, JWT, Testcontainers, CI/CD, live deploy).

## Stage 2 — Master Document (`career-copilot.md`)

The planning docs were merged into a single source of truth. It locked **eight foundational decisions** before any code:

1. **Resume rendering = structured JSON → LaTeX template → compiled PDF.** The LLM never emits LaTeX or PDFs; a catalog of 4–5 repo-stored templates is filled deterministically and compiled with Tectonic.
2. **Provider-agnostic LLM via Spring AI `ChatClient`** — OpenAI-compatible `base-url` (OpenRouter in prod, local models optional in dev), tiered config-driven model selection, never hardcoded.
3. **Job ingestion is the entry point (Phase 1) and pluggable** behind a `JobSource` interface — real APIs + manual paste first, HTML scraping later.
4. **Multi-user from day 0** — JWT auth, per-user isolation, deploy at every checkpoint (not once at the end).
5. **Interview-data capture early, mock-interview trainer later.**
6. **Shared, anonymized public interview intel; everything else strictly private.**
7. **Cover letters on demand only**, same JSON→template→PDF pipeline.
8. **ATS scoring = real-ATS keyword/coverage techniques + semantic (embedding) augmentation**, validated on a ~20-JD labeled set.

It also resolved a key tension: local models are free but won't fit a cheap cloud host → code stays provider-agnostic; dev uses local/free, prod uses cheap OpenRouter models, keeping the $25 budget intact.

The document defined the **8-phase build order** (0 Foundation → 1 Job Portal → 2 Experience Corpus → 3 Resume Tailoring → 4 ATS Scorer → 5 Tracker → 6 Public Intel → 7 Mock Interview → 8 Hardening/Metrics), each phase a vertical slice ending in a redeploy.

## Stage 3 — Claude Code SDD Setup (completed 2026-06-30)

The build environment was configured to enforce spec-driven development:

- **`CLAUDE.md`** — project memory with CRITICAL rule sections: LLM rules (everything via `LlmGateway`, config-driven models, token logging), resume rule (JSON only), guardrail rules (PII redaction, injection handling, grounded-only, JSON schema validation), privacy rules (per-user isolation, anonymize-before-share), testing rules (Testcontainers, LLM always mocked), deploy rule (additive migrations, redeploy each phase).
- **`specs/`** — `SPEC_TEMPLATE.md` + one spec per phase (0–8). No implementation without an **APPROVED** spec.
- **7 skills** (`latex-resume-renderer`, `spring-ai-rag`, `ats-scoring`, `guardrails`, `anonymization`, `jobsource-adapter`, `spec-author`) and **8 slash commands** (`/spec`, `/implement-spec`, `/new-feature`, `/add-llm-task`, `/migration`, `/test`, `/review`, `/deploy-checkpoint`).
- **Hooks** in `.claude/settings.json` (format/compile on edit, secret scan, LLM-rule and DB-safety guards).
- **The SDD loop** used for every phase since: `/spec` → human review & approval → `/implement-spec` → `/test` → `/review` → `/deploy-checkpoint` → update CLAUDE.md.

---

## Phase 0 — Foundation & Walking Skeleton ✅ (committed 2026-07-01, **deployed**)

**Spec:** `specs/phase-0-foundation.md` — IMPLEMENTED.

**Built:** Spring Boot 3.5 skeleton (Java 21, Maven wrapper), PostgreSQL 16 + pgvector via Docker/Flyway, JWT auth with real multi-user accounts, minimal React + TypeScript shell, GitHub Actions CI running Testcontainers, Dockerfile, and the **first deploy to DigitalOcean App Platform** (deploy checkpoint 0) with health-check smoke test.

**Decisions:** `users.id` is **UUID** — this later forced all `user_id` FKs to be UUID even though the planning schema modelled them as `BIGSERIAL` (reconciled in later phases).

## Phase 1 — Job Ingestion / Portal ✅ (committed 2026-07-02 00:35 + 01:14)

**Spec:** `specs/phase-1-job-ingestion.md` — IMPLEMENTED (deploy pending).

**Built:**
- Pluggable **`JobSourceAdapter`** interface with adapters: **RemoteOK** (live), **Adzuna** (built but disabled — needs API keys), **manual JD paste**; a follow-up commit added **keyless ATS sources (Greenhouse, Lever)**.
- Normalized `job_postings` table, public browse/search endpoints, React job portal (list, search, detail pages).

**Decisions / conventions established:**
- Native Postgres enums (`CREATE TYPE ... AS ENUM`) mapped with Hibernate `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` — became the project-wide enum convention.
- Migration numbering continued from Phase 0; V5 used for ATS job sources.

**Outstanding:** Deploy checkpoint 1 (DigitalOcean redeploy) was deferred — it is still the pending gate that Phases 2–4 have stacked behind.

## Phase 2 — Candidate Profile & Experience Corpus ✅ (implemented 2026-07-02, 81/81 tests green)

**Spec:** `specs/phase-2-profile-corpus.md` — APPROVED → implemented (status flips to IMPLEMENTED only after deploy).

**Built:**
- `candidate_profiles` + `experience_entries` (migrations **V6–V8**), owner-scoped profile/experience CRUD, React profile page.
- **Async embedding pipeline** via `shared/EmbeddingGateway` — Ollama `nomic-embed-text`, 768-dim, PII-redacted inputs, token-logged; hash-based re-embed detection; `findTopKByVector` retrieval primitive (the RAG foundation for Phase 3).

**Decisions:**
- **Embedding convention locked:** all embedding calls go through `EmbeddingGateway`, never `EmbeddingModel` directly; model/dimension config-driven; `vector(768)` columns must match `EMBEDDING_DIMENSION`; mapping via `hibernate-vector` (`SqlTypes.VECTOR`), retrieval via HQL `cosine_distance()`.
- **Prod has no Ollama** → entries stay `embeddingStatus: PENDING`; a `@Scheduled` retry sweep (`embedding.retry-cron`) embeds them once `OLLAMA_BASE_URL` is reachable. Graceful degradation instead of a hard dependency.

**Issues resolved:**
- Migrations renumbered to V6–V8 because V5 was already taken by the ATS-job-sources commit.
- `text_hash` had to be `VARCHAR(64)`, not `CHAR` — Hibernate schema validation rejects `bpchar`.

## Phase 3 — Resume Tailoring (RAG + JSON + LaTeX) ✅ (committed 2026-07-02 10:36, 134/134 tests green)

**Spec:** `specs/phase-3-resume-tailoring.md` — IMPLEMENTED (deploy pending). This is the core-value phase.

**Built:**
- **`LlmGateway`** (`shared/`) — OpenRouter via the Spring AI OpenAI starter, tiered `llm.tiers` config (EXTRACTION cheap / TAILORING strong), token logging per call.
- **Four guardrails** in `shared/guardrail/`: `PromptInjectionAdvisor` (sanitize-and-fence untrusted JDs), `PiiRedactionAdvisor`, `JsonSchemaValidator` (retry-once → 502 on repeated failure), `GroundingValidator` (every experience item must carry a real `sourceEntryId` → 422 otherwise — no invented experience).
- **RAG tailor pipeline** (`tailoring/`): embed JD → `findTopKByVector` → JSON-only LLM call → persist (`resume_versions` V9, `cover_letters` V10) → deterministic **JMustache + Tectonic** render. 4 resume templates + 1 cover-letter template registered in `TemplateRegistry`. Cover letters generated on demand only.
- **V11** added `full_name`/`phone` to `candidate_profiles` (resume header needs them; users table only stores email).
- React: template picker, tailor/cover-letter actions, resume history, blob PDF download. CI + Dockerfile now install Tectonic.

**Decisions taken at spec approval:**
- PDFs stored as **BYTEA in Postgres** (not DO Spaces); the JSON payload is the source of truth and the PDF endpoint re-renders when the blob is NULL.
- Chat LLM = **OpenRouter for both dev and prod**; Ollama stays embeddings-only (and isn't installed on the dev Mac, so dev embeddings stay PENDING).
- **PII never round-trips through the LLM provider:** contact info / candidate name are injected into the resume JSON *after* the LLM call.
- `PageResponse` promoted from `jobs/dto` to `shared/` (used by 2+ features — architecture rule 5).

**Issues resolved:**
- Migrations renumbered V9/V10 (V8 already taken by Phase 2).
- `src/test/resources/application.yml` **fully replaces** the main config — tests must set `spring.ai.model.chat: openai` with a dummy key so `ChatClient.Builder` exists (`LlmGateway` is mocked everywhere).
- `principal.getName()` returns the **email**, not the userId — services resolve users via `UserRepository.findByEmail`.
- The spotless format-on-edit hook once stripped imports that didn't compile yet — re-added and recompiled.
- LaTeX template gotcha: `\noindent{{var}}` glues into an undefined control sequence — always leave a space after a macro before a Mustache tag.
- Render tests use `assumeTrue(renderer.isAvailable())` so they skip cleanly where Tectonic isn't installed.

## Phase 4 — ATS Gap Scorer ✅ (committed 2026-07-02 10:59, 167/167 tests green)

**Spec:** `specs/phase-4-ats-scorer.md` — IMPLEMENTED v1.1 (deploy pending).

**Built (`scoring/` slice):**
- **LLM JD keyword extraction** — `LlmTask.EXTRACTION`, schema `jd-keyword-extraction`, through `LlmGateway` with all guardrails.
- **Deterministic composite score 0–100:** `(requiredCoverage×0.6 + preferredCoverage×0.25 + keywordDensity×0.15)×100`, with alias-normalized 1–3-gram matching via `SkillAliasMap` (curated `resources/scoring/skill-aliases.yaml`).
- **Embedding-only near-misses** (`SemanticAugmenter`, cosine ≥ threshold) and **template-based fixes** (`FixGenerator`).
- `scoring_results` snapshots (**V12**); endpoints `POST /api/v1/scoring/score` and `GET /api/v1/scoring/results/{resumeVersionId}` (owner-scoped, with a dedicated ownership integration test).
- **Eval harness:** `AtsEvalHarnessTest` + `src/test/resources/eval/ats-labeled-set.json` (20 JDs) — deterministic and LLM-free; the headline-metric artifact.
- React: gap-report view + score actions on the Resumes and JobDetail pages.

**Decisions taken with the user (recorded in spec v1.1):**
- JD keyword extraction is an **LLM task**, not deterministic NLP (superseding the older `ats-scoring` skill doc); all scoring *math* stays deterministic.
- Composite score shape and weights as above; keywordDensity = fraction of all deduped JD keywords present in the resume.
- Near-miss threshold **0.75** (`SCORING_NEARMISS_THRESHOLD`), semantic toggle `SCORING_SEMANTIC_ENABLED`; augmentation is embeddings-only — the planned `SCORING_AUGMENTATION` LLM tier is intentionally unused.
- Migration became **V12** (the spec's V10 number was stale); `user_id` is UUID per the Phase 0 reconciliation.

**Issues resolved:**
- `src/test/resources/application.yml` override bit again: new `@Value` scoring properties had to be added there too, or every integration test failed at context load. Fixed by adding the `scoring:` block.

**Known caveat (flagged honestly):** the 20-JD eval set uses *generated golden labels computed with the same formula* (mean delta 0.001) — it is a **regression golden test**, not an independent human-labeled benchmark. For a true headline metric, the labels need independent hand-scoring.

## Phase 5 — Application & Interview Tracker ✅ (implemented 2026-07-05, 34/34 new unit tests green)

**Spec:** `specs/phase-5-application-tracker.md` — APPROVED → implemented (flips to IMPLEMENTED after deploy). A full DRAFT already existed from spec-authoring earlier in the project; it needed one correction before approval (see Decisions below), not a rewrite.

**Built (`tracker/` slice):**
- **`applications`** (**V13**) and **`interview_logs`** (**V14**) tables — pure CRUD + state machine, no LLM involved.
- **`ApplicationStatusMachine`** — validated linear happy path `SAVED → APPLIED → PHONE_SCREEN → TECHNICAL_INTERVIEW → FINAL_ROUND → OFFER`, with any non-terminal status allowed to move to `REJECTED`/`WITHDRAWN`; illegal transitions (skips, backward moves, moves out of a terminal state) → `400`.
- **`ApplicationService`** — snapshot defaulting (`jobTitleSnapshot`/`companySnapshot` pulled from the linked `job_postings` row when omitted, required manually otherwise), auto-sets `appliedAt` on the first transition into `APPLIED`, ownership checks on every linked `resumeVersionId`/`coverLetterId`.
- **`InterviewLogService`** — log CRUD scoped to an owned application; `contribute` endpoint stub (`202`, `contribution_status = STAGED`) that Phase 6 will complete; `409 ALREADY_CONTRIBUTED` on a repeat call.
- Endpoints: `GET/POST /api/v1/tracker/applications`, `PUT /api/v1/tracker/applications/{id}`, `GET/POST /api/v1/tracker/applications/{id}/interview-logs`, `POST .../interview-logs/{logId}/contribute` — all owner-scoped via the existing `Principal.getName()` → email → `UserRepository.findByEmail` pattern (no `CurrentUserService` exists in this codebase; every feature resolves the user itself).
- Tests: `ApplicationStatusMachineTest` (24 cases), `ApplicationServiceTest`, `InterviewLogServiceTest` (unit, Mockito) + `ApplicationTrackerIntegrationTest`, `ApplicationOwnershipIntegrationTest`, `InterviewLogIntegrationTest` (Testcontainers).
- React: `ApplicationsPage` (kanban-by-status board), `ApplicationDetailPage` (advance/reject/withdraw actions + interview-log form + contribute button), a "Track this application" action added to `JobDetailPage`, new `api.ts` functions/types, routes registered in `App.tsx`.

**Decisions taken at spec approval:**
- **Migration renumbering:** the DRAFT spec's own `V11`/`V12` were already taken by Phase 3/4 (`V11__profile_contact_fields.sql`, `V12__scoring_results.sql`) — renumbered to **V13**/**V14** before approval. The DRAFT's flagged "BIGSERIAL vs UUID" reconciliation question was actually already correct (matches `resume_versions`/`candidate_profiles`/`scoring_results`) and needed no change.
- `ApplicationRequest` is one shared DTO for both create and update (PUT is a full field replace, not a partial patch) — simpler than maintaining two near-identical DTOs for a 9-field resource.
- No `GET /applications/{id}` endpoint exists (not in the approved spec's API surface) — `ApplicationDetailPage` fetches the list and filters client-side, mirroring the precedent already used in `JobDetailPage` (filtering `listResumes()` by job id).
- Interview-log repository queries filter by `userId` directly (`findByApplicationIdAndUserIdOrderByRoundNumberAsc`, `findByIdAndApplicationIdAndUserId`) rather than relying solely on the parent-application ownership check — caught and fixed during `/review`, to match the defense-in-depth pattern `ScoringResultRepository` already established.

**Issues resolved:**
- Mockito nested-stubbing bug in early test drafts: `when(x).thenReturn(Optional.of(helperThatItselfCallsWhen()))` corrupts Mockito's stub-tracking. Fixed by extracting the inner mock to a local variable before the outer `when(...)` call.
- Docker/Testcontainers is not available in this working environment — the 6 new integration test classes compile and are written against the same `@Testcontainers` + `pgvector/pgvector:pg16` pattern as every other phase, but could not be executed here. **Must be run locally (`./mvnw test` with Docker running) before this phase's Definition of Done is fully satisfied.**

---

## Cross-Phase Threads

- **Migration history:** V1–V4 (Phase 0 foundation/auth) → V5 (ATS job sources) → V6–V8 (profiles, experience, embeddings) → V9–V11 (resume versions, cover letters, contact fields) → V12 (scoring results) → V13–V14 (applications, interview logs). All additive, per the deploy rule. Numbering collisions happened three times now (V5, V8, and the Phase 5 spec's stale V11/V12) and were resolved by renumbering forward each time.
- **Recurring gotcha:** the test `application.yml` fully replacing main config caused failures in both Phase 3 and Phase 4 — any new required property must be mirrored there.
- **Guardrail architecture** (built Phase 3, reused Phase 4): every LLM call passes through injection sanitization, PII redaction, JSON schema validation with retry, and (for tailoring) grounding validation.
- **Privacy discipline:** every user-data query is owner-scoped; PII is kept out of LLM round-trips; the shared-intel anonymization layer is still ahead (Phase 6).

## Outstanding Work / Next Steps

1. **Run the full test suite with Docker available** — confirm the 6 new Phase 5 Testcontainers integration tests pass (`ApplicationTrackerIntegrationTest`, `ApplicationOwnershipIntegrationTest`, `InterviewLogIntegrationTest`); they compile and follow the established pattern but could not execute in this session's Docker-less sandbox.
2. **Deploy checkpoint 1 (highest priority)** — one DigitalOcean redeploy now ships Phases 1–5. No new env vars for Phase 5 itself; still required from earlier phases: `OPENROUTER_API_KEY`, `LLM_TAILORING_MODEL`, `LLM_EXTRACTION_MODEL`, `RETRIEVAL_TOP_K`, `TECTONIC_TIMEOUT_SECONDS`, `SCORING_NEARMISS_THRESHOLD` (0.75), `SCORING_SEMANTIC_ENABLED` (true). Prod embeddings stay PENDING until `OLLAMA_BASE_URL` points at a live Ollama instance; the retry sweep then picks them up. Docker image and CI already install Tectonic.
3. **Headline metric hardening** — replace the generated golden labels in the ATS eval set with independent hand-scored labels to make the before/after improvement number defensible.
4. Later phases: 6 (anonymized company intel — completes the `contribute` stub from Phase 5), 7 (mock-interview trainer + SM-2), 8 (metrics, observability, OpenAPI docs, polish).

## Timeline at a Glance

| Date | Milestone |
|---|---|
| pre-2026-06-30 | Planning folder + `career-copilot.md` master document; 8 decisions locked |
| 2026-06-30 | Claude Code SDD environment set up (CLAUDE.md, specs, skills, commands, hooks) |
| 2026-07-01 18:15 | Phase 0 committed; first DigitalOcean deploy (checkpoint 0) |
| 2026-07-02 00:35 | Phase 1 committed (job ingestion & portal) |
| 2026-07-02 01:14 | Keyless ATS job sources added (Greenhouse, Lever) |
| 2026-07-02 04:15 | Phase 2 committed (profile & experience corpus, 81/81 tests) |
| 2026-07-02 10:36 | Phase 3 committed (resume tailoring, 134/134 tests) |
| 2026-07-02 10:59 | Phase 4 committed (ATS gap scorer, 167/167 tests) |
| 2026-07-05 | Phase 5 implemented (application & interview tracker, 34 new unit tests green; integration tests pending a Docker-available run) |
| — pending — | Deploy checkpoint 1 (Phases 1–5) |
