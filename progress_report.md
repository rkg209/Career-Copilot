# Career Copilot — Progress Report

> **As of:** 2026-07-05 · **Branch:** `main` · **Tests:** **242/242 green (full suite, Docker up) — all 22 Testcontainers integration tests now executed and passing, including Phases 5 & 6** · **Current phase:** Phase 7 — Mock Interview Trainer (next up)
>
> This report tracks the project sequentially: from the planning folder and the master document (`career-copilot.md`), through the Claude Code spec-driven-development (SDD) setup, and phase by phase through Phase 6. Every stage records what was done, the decisions taken, and the issues resolved.

---

## Snapshot — Where the Project Stands Today

| Item | State |
|---|---|
| Phases complete (code + tests) | 0, 1, 2, 3, 4, 5, 6 |
| Deployed to DigitalOcean | Phase 0 only (deploy checkpoint 0) |
| **Deploy checkpoint 1** | **PENDING — now covers Phases 1–6 stacked together** |
| Test suite | **242/242 green** — full `./mvnw test` with Docker; every Testcontainers integration test (all phases 0–6) now runs and passes |
| Specs | Phases 0–6 written & implemented; Phases 7–8 specs drafted |
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

## Phase 6 — Company Interview Intel ✅ (implemented 2026-07-05, 159/159 unit tests green)

**Spec:** `specs/phase-6-company-intel.md` — DRAFT → APPROVED → IMPLEMENTED. A near-complete DRAFT
already existed from earlier spec-authoring; it needed two corrections before approval (see
Decisions below), not a rewrite.

**Built (`intel/` slice):**
- **`company_interview_intel`** (**V15**) — single shared table (`TEXT company`, `BIGSERIAL` id,
  `is_published`, `staging_batch_id UUID`, `published_at`), no `user_id`/`application_id` columns
  by design; reuses the `interview_round_type` enum from V14. GIN trigram index on `company`.
- **`PiiScrubber`** — deterministic scrub of free text: delegates email/phone/SSN to the shared
  `PiiRedactionService`, adds URL stripping and a Title-Case-bigram person-name heuristic (with a
  stoplist for common technical/process bigrams like "System Design" so real content survives).
- **`AnonymizationService`** — sole writer of the intel table; builds a `CompanyIntelEntry` from an
  `InterviewLog` + its `Application` (for company/title), normalizes role category via keyword
  matching, reuses (or creates) a `staging_batch_id` per company.
- **`ContributionService`** — replaces the Phase 5 stub end-to-end: ownership check → 409 if
  already contributed → anonymize+stage → count staged+published rows for the company → if ≥
  threshold (`INTEL_MIN_AGGREGATION`, default 3), flip that company's staged batch to published
  in place and flip the corresponding source `interview_logs` rows to `PUBLISHED` (found via an
  `applications.company_snapshot` lookup, since the intel table intentionally carries no back-link
  to the source log).
- **`IntelController`/`IntelService`** — two public, unauthenticated endpoints:
  `GET /api/v1/intel/companies` (paginated per-company aggregates, published rows only) and
  `GET /api/v1/intel/companies/{companyName}` (grouped by round type, 404 if nothing published).
  `SecurityConfig` permits `GET /api/v1/intel/**` alongside the existing public `/api/v1/jobs/**`.
- **Tracker endpoint response change:** `POST .../interview-logs/{logId}/contribute` now returns
  **200** with `{status, company, publishedCountForCompany, threshold}` (was `202`/`{"status":
  "PENDING"}` in the Phase 5 stub) — a deliberate breaking change to the Phase 5 contract, since
  the stub was explicitly documented as temporary.
- Tests: `PiiScrubberTest`, `AnonymizationServiceTest`, `ContributionServiceTest`,
  `IntelServiceTest` (unit, Mockito) + `AnonymizationPiiLeakTest` (crafted-PII guardrail) +
  `IntelNoUserLeakTest` (reflection-based structural guardrail — fails if any future field named
  like `userId`/`applicationId`/`email` is ever added to the entity or DTOs) + three Testcontainers
  integration tests (`ContributionIntegrationTest`, `IntelBrowseIntegrationTest`,
  `IntelPrivacyIntegrationTest`) written but not executable in this Docker-less sandbox.
- React: `CompanyIntelPage` (public list, paginated) + `CompanyIntelDetailPage` (public, grouped by
  round type) at `/intel` and `/intel/:companyName`; `ApplicationDetailPage`'s contribute button
  now surfaces the STAGED/PUBLISHED outcome and per-log contribution status.

**Decisions taken with the user at spec approval:**
- **Schema design conflict resolved in favor of the spec**, not the older `.claude/skills/anonymization.md`
  doc: single `company_interview_intel` table with in-place `is_published` flip, no separate
  `companies` table, no `company_intel_staging` table, no hourly `@Scheduled` promotion job. The
  skill doc was rewritten to match (class names, schema, threshold-check-is-synchronous) so it
  no longer describes a design nobody implemented.
- **Migration renumbering:** the DRAFT spec's own `V13` was already taken by Phase 5's
  `applications` table — renumbered to **V15** (next free version after `V14__interview_logs.sql`).
- `ApplicationService.ownedApplication`/`resolveUser` were widened from package-private to
  `public` so `ContributionService` (a different feature package, `intel` vs `tracker`) could reuse
  the existing ownership-check pattern instead of duplicating it.

**Issues resolved:**
- The `PiiScrubber`'s name-redaction heuristic (any two consecutive Title-Case words → `[PERSON]`)
  would have false-positived on technical vocabulary like "System Design" or "Dynamic Programming"
  — added a small stoplist so real interview content survives scrubbing.
- Docker/Testcontainers unavailable in this sandbox (same caveat as Phase 5) — the 3 new
  integration test classes compile and follow the established `@Testcontainers` +
  `pgvector/pgvector:pg16` pattern but could not execute here. **Must be run locally (`./mvnw test`
  with Docker running) before this phase's Definition of Done is fully satisfied.**

---

## Phase 6.5 — Full-Suite Audit & Fixes (2026-07-05)

A strong end-to-end verification pass was run against the specs/planning before deploy. Docker was
started so the Testcontainers integration suite could execute for the first time since Phase 5 — this
uncovered a critical regression that unit tests could never have caught.

**Findings & resolutions:**

1. **CRITICAL — schema-validation mismatch broke the entire integration suite *and* app startup.**
   Four columns were declared `SMALLINT` in migrations but mapped as `Integer`/`int` in the JPA
   entities: `interview_logs.round_number` / `duration_minutes` / `difficulty` (V14) and
   `company_interview_intel.difficulty` (V15). With `ddl-auto: validate`, Hibernate rejects each at
   context load, so **all 83 integration tests errored** (they share one `EntityManagerFactory`) and,
   more seriously, the Spring Boot app would **fail to boot in production** the same way. Introduced
   in Phase 5 (V14), compounded in Phase 6 (V15); masked because the integration suite had never been
   run with Docker. **Fix:** changed the four columns to `INTEGER` in V14/V15 (migrations never
   deployed, so edited in place). Result: full suite **242/242 green**.

2. **LLM response caching (CLAUDE.md CRITICAL rule) was unimplemented.** Added a bounded (256-entry)
   access-ordered LRU cache inside `LlmGateway`, gated on `groundingContext == null` so **only
   non-grounded, user-agnostic calls are cached** — in practice the EXTRACTION tier (public JD →
   keywords). Grounded tasks (resume tailoring, cover letters) are deliberately never cached, since
   their output references a specific user's experience corpus and must not be shared across callers.

3. **Company-snapshot whitespace inconsistency (data-integrity edge).** `applications.company_snapshot`
   was stored untrimmed while `AnonymizationService` writes the intel `company` as `.trim()`ed —
   so a value like `"Google "` would publish intel under `"Google"` but leave the source
   `interview_log` stuck in `STAGED` (the `findIdsByCompanySnapshot` log-flip lookup wouldn't match).
   **Fix:** `ApplicationService` now trims `jobTitleSnapshot`/`companySnapshot` on create (update path
   never mutates snapshots). Case-fragmentation across `"Google"`/`"google"` remains a deliberate
   design choice (exact-match company identity), noted for a future normalization decision.

**Still open (not code-fixable here):** the 20-JD ATS eval "golden" labels are generated by the same
formula they validate — a regression golden, not an independent human-labeled benchmark. Replacing
them with hand-scored labels is needed to make the headline before/after metric defensible.

## Phase 2E — Profile Expansion: Full Resume Corpus ✅ (implemented 2026-07-08, 331/331 tests green)

**Trigger:** analysis of 50 shortlisted-student resumes (`Temp_resumes/`, IIT Bombay placement
format) showed the current profile (basic info + work experience) misses most of what real
student resumes are made of.

**Analysis findings (section coverage across 50 resumes):** education table 50/50, scholastic
achievements 42/50, extracurriculars/hobbies 38/50, positions of responsibility 36/50,
coursework lists 32/50, projects (course/self/research, usually with guide professor) ~48/50,
thesis/seminar 28/50, categorized technical skills ~25/50, certifications 13/50,
publications/patents 10/50, summary/objective paragraph 0/50. ~50 bullets per resume, almost all
action-verb + quantified-outcome; projects — not jobs — are the backbone of student resumes.

**Spec drafted:** `specs/profile-expansion-full-resume-corpus.md` (DRAFT v1.0). Key design
decisions:
- Generalize `experience_entries` into typed corpus entries (`entry_type`: WORK, INTERNSHIP,
  PROJECT, POSITION_OF_RESPONSIBILITY, PUBLICATION + `project_type`, `guide`, `context`, `url`)
  so the existing embedding/retrieval pipeline is reused unchanged (V20).
- New structured-fact tables: `education_entries` (V21), `skill_groups` (V22),
  `profile_highlights` (V23: scholastic achievements / certifications / extracurriculars),
  plus `coursework` + `areas_of_interest` arrays on `candidate_profiles` (V24).
- Tailoring integration: LLM selects grounded `projects` + `positionsOfResponsibility`
  (sourceEntryId grounding extended); education/skills/achievements/certifications/coursework
  injected deterministically post-LLM (contactInfo pattern); all 4 LaTeX templates gain the new
  optional sections.
- Deliberately excluded: gender/DOB (discrimination-sensitive), resume-PDF import (future),
  per-course grades.

**Implemented same day (spec APPROVED by user, then `/implement-spec`):**
- Migrations V20–V24 applied (typed corpus, education, skill groups, highlights, profile lists).
- `ExperienceText` embedding text now carries `[TYPE]` label, guide, context (hash-sensitive, so
  edits re-embed); `TailoringPrompts` labels entries by type and routes them to
  `experience[]`/`projects[]`/`positionsOfResponsibility[]`; resume JSON schema extended with
  `anyOf` (at least one evidence section) and per-item `sourceEntryId` grounding; new
  `ResumeSectionAssembler` injects education/skills/achievements/certifications/coursework
  post-LLM; `LatexRenderer` injects `hasX` presence flags; all 4 templates extended and verified
  by real Tectonic renders (caught and fixed a LaTeX paragraph-break bug in 3 templates —
  education entries merged without `\par`).
- New tests: `ProfileSectionsIntegrationTest`, `TypedCorpusIntegrationTest`,
  `ExperienceTextTest`, `ResumeSectionAssemblerTest`, extended `GroundingValidatorTest`,
  `TailoringIntegrationTest` (section-injection test), `LatexRendererTest` (full-sections render),
  `ProfileServiceTest`. Full suite `./mvnw test` with Docker: **331/331 green**; frontend
  `tsc` + `vite build` green.
- Gotcha (recurring this session): the format-on-save hook strips imports that are momentarily
  unused between two sequential edits — re-add imports after usages exist.

**Also this session (2026-07-08):** template preview thumbnails added to the tailor page —
`TemplateRegistry`/`TemplateResponse` expose `previewImagePath`, real dummy-resume renders of all
4 templates committed as PNGs under `frontend/public/template-previews/`, side-panel preview on
`TailorPage`. Full suite green, frontend build green.

## Cross-Phase Threads

- **Migration history:** V1–V4 (Phase 0 foundation/auth) → V5 (ATS job sources) → V6–V8 (profiles, experience, embeddings) → V9–V11 (resume versions, cover letters, contact fields) → V12 (scoring results) → V13–V14 (applications, interview logs) → V15 (company interview intel). All additive, per the deploy rule. Numbering collisions happened four times now (V5, V8, the Phase 5 spec's stale V11/V12, and the Phase 6 spec's stale V13) and were resolved by renumbering forward each time.
- **Recurring gotcha:** the test `application.yml` fully replacing main config caused failures in both Phase 3 and Phase 4 — any new required property must be mirrored there.
- **Guardrail architecture** (built Phase 3, reused Phase 4): every LLM call passes through injection sanitization, PII redaction, JSON schema validation with retry, and (for tailoring) grounding validation.
- **Privacy discipline:** every user-data query is owner-scoped; PII is kept out of LLM round-trips; the shared-intel anonymization layer (Phase 6) reuses the same `PiiRedactionService` regex patterns for its own scrubbing.

## Outstanding Work / Next Steps

1. ~~Run the full test suite with Docker available~~ **DONE (2026-07-05):** full `./mvnw test` with Docker is **242/242 green**; every Testcontainers integration test across all phases now executes and passes. This run uncovered and fixed the critical SMALLINT/Integer schema-validation bug (see Phase 6.5 above) that would otherwise have blocked app startup at deploy.
2. **Deploy checkpoint 1 (highest priority)** — one DigitalOcean redeploy now ships Phases 1–6. New env vars for Phase 6: `INTEL_MIN_AGGREGATION` (default 3), `INTEL_LLM_ASSIST_ENABLED` (default false, unused placeholder — no LLM assist implemented this phase). Still required from earlier phases: `OPENROUTER_API_KEY`, `LLM_TAILORING_MODEL`, `LLM_EXTRACTION_MODEL`, `RETRIEVAL_TOP_K`, `TECTONIC_TIMEOUT_SECONDS`, `SCORING_NEARMISS_THRESHOLD` (0.75), `SCORING_SEMANTIC_ENABLED` (true). Prod embeddings stay PENDING until `OLLAMA_BASE_URL` points at a live Ollama instance; the retry sweep then picks them up. Docker image and CI already install Tectonic.
3. **Headline metric hardening** — replace the generated golden labels in the ATS eval set with independent hand-scored labels to make the before/after improvement number defensible.
4. Later phases: 7 (mock-interview trainer + SM-2, can now draw on the Phase 6 intel pool), 8 (metrics, observability, OpenAPI docs, polish).

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
| 2026-07-05 | Phase 6 implemented (company interview intel, 159/159 unit tests green; integration tests pending a Docker-available run) |
| 2026-07-05 | Full-suite audit + fixes: critical V14/V15 SMALLINT↔Integer schema bug fixed, LLM response cache added, company-snapshot trim fix. Full `./mvnw test` with Docker **242/242 green** |
| 2026-07-05 | Frontend redesign — "Modern SaaS (dark-first)" theme: Tailwind v4 + Inter, light/dark toggle, shared `AppLayout`/`AuthLayout` shell + `components/ui/` primitives, all 13 pages + `GapReport` restyled. Design system documented in `frontend/DESIGN.md`. `npm run build`/`lint`/`tsc` green; both themes verified visually. No backend/API changes |
| 2026-07-08 | Template preview thumbnails on tailor page (real dummy renders of all 4 templates) |
| 2026-07-08 | 50-resume corpus analyzed; Phase 2E profile-expansion spec drafted (`specs/profile-expansion-full-resume-corpus.md`, DRAFT) |
| 2026-07-08 | Phase 2E implemented (typed corpus V20, education V21, skill groups V22, highlights V23, profile lists V24; tailoring sections + templates + profile UI). 331/331 tests green |
| — pending — | Deploy checkpoint 1 (Phases 1–6) |
