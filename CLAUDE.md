# Career Copilot — Project Memory

## What This Project Is

**As an AI/ML application:** Multi-user RAG pipeline — experience corpus embedded in pgvector, retrieval-augmented resume tailoring, LLM-driven JSON extraction, ATS scoring, anonymized interview-intel pooling.

**As an SDE/full-stack project:** Spring Boot 3.5 REST API + React/TypeScript SPA, JWT auth, Flyway-managed PostgreSQL schema, Docker + DigitalOcean App Platform deploy, GitHub Actions CI.

---

## Current Phase

**Phase 3 — Resume Tailoring** (next)

**Done:**
- **Phase 0 — Foundation & Walking Skeleton** — Spring Boot skeleton, Postgres+pgvector, JWT auth, React shell, CI, first deploy.
- **Phase 1 — Job Ingestion / Portal** — pluggable `JobSourceAdapter` (RemoteOK live, Adzuna built/disabled, manual paste), normalized `job_postings`, public browse/search, React portal. Code complete. **Deploy checkpoint 1 (DigitalOcean redeploy) still pending.**
- **Phase 2 — Candidate Profile & Experience Corpus** — `candidate_profiles` + `experience_entries` (V6–V8), owner-scoped profile/experience CRUD, async embedding via `EmbeddingGateway` (Ollama `nomic-embed-text`, 768-dim, PII-redacted, token-logged), hash-based re-embed detection, `findTopKByVector` retrieval primitive, React profile page. Code complete, 81/81 tests green. **Deploy pending (ships with checkpoint 1). Prod embeddings stay PENDING until `OLLAMA_BASE_URL` is live; retry sweep picks them up.**

> Embedding convention (set in Phase 2): all embedding calls go through `shared/EmbeddingGateway`
> (never `EmbeddingModel` directly). Model/dimension are config-driven; `vector(768)` columns must
> match `EMBEDDING_DIMENSION`. Vector mapping uses `hibernate-vector` (`@JdbcTypeCode(SqlTypes.VECTOR)`),
> retrieval uses HQL `cosine_distance()`.

> Enum convention (set in Phase 1): native Postgres `CREATE TYPE ... AS ENUM` mapped with Hibernate
> `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`. Note: `users.id` is `UUID` (deployed in Phase 0), so all
> `user_id` FKs in later phases are `UUID` (planning schema modelled it as `BIGSERIAL` — reconciled to UUID).

---

## Tech Stack (pinned versions)

| Layer | Technology | Version |
|---|---|---|
| Runtime | Java | 21 |
| Framework | Spring Boot | 3.5.x |
| AI | Spring AI | 1.1.x |
| Security | Spring Security (JWT) | with Spring Boot 3.5 |
| ORM | Spring Data JPA + Hibernate | with Spring Boot 3.5 |
| Migrations | Flyway | with Spring Boot 3.5 |
| Database | PostgreSQL + pgvector | 16 + 0.7.x |
| LLM (prod) | OpenRouter (OpenAI-compatible) | config-driven |
| LLM (dev) | Ollama | config-driven |
| Resume render | Tectonic / pdflatex | system install |
| Frontend | React + TypeScript | 18 / 5.x |
| Build | Maven Wrapper (./mvnw) | 3.9.x |
| CI | GitHub Actions | — |
| Hosting | DigitalOcean App Platform + Managed Postgres | — |

---

## Architecture Rules

1. **Package by feature** under `com.careercopilot`: `auth`, `jobs`, `profile`, `tailoring`, `scoring`, `tracker`, `intel`, `shared`.
2. **Layering within a feature**: `Controller → Service → Repository`. No skipping layers.
3. **DTOs at the boundary** — controllers receive/return DTOs, never JPA entities. Map inside Service or a dedicated Mapper.
4. **No business logic in controllers** — controllers validate input, delegate to Service, return ResponseEntity.
5. **Shared utilities only in `shared/`** — anything used by two or more features lives there.
6. **One Flyway migration per schema change** — file naming: `V{N}__{snake_case_description}.sql`.

---

## CRITICAL — LLM Rules

- **All LLM calls go through `LlmGateway`** using Spring AI `ChatClient`. Never call OpenAI/OpenRouter/Ollama SDK directly.
- **Never hardcode a model name or provider URL** in Java code. All model selection is config-driven via `LlmTaskConfig` in `application.yml`.
- **Tiered model config**: Use `EXTRACTION` tier (cheap, fast) for bulk/extraction tasks; `TAILORING` tier (strong) for resume generation; `SCORING` tier for ATS analysis.
- **Token budget**: Set `maxTokens` per task tier in config. Log input + output tokens for every call.
- **Cache**: Enable Spring AI response caching for identical prompts.
- **Log tokens**: Every LlmGateway call must log `{task, inputTokens, outputTokens, modelUsed}` at DEBUG level.

---

## CRITICAL — Resume Rule

- **LLM emits structured JSON ONLY** — never LaTeX, never raw text, never PDF.
- The JSON schema for resume data is defined in `.claude/skills/latex-resume-renderer.md`.
- **Rendering is deterministic**: JSON → selected LaTeX template from template catalog → Tectonic → PDF.
- Templates live in `src/main/resources/templates/latex/`. Adding a template = adding a `.tex.mustache` file + registering in `TemplateRegistry`.
- **Never put LaTeX in an LLM prompt** — prompts ask for structured data, not formatting instructions.

---

## CRITICAL — Guardrail Rules

All guardrails execute inside `LlmGateway` before/after every LLM call:

1. **PII Redaction** (`PiiRedactionAdvisor`): Strip emails, phone numbers, SSNs, addresses from all LLM inputs and outputs before logging or persisting.
2. **Prompt Injection** (`PromptInjectionAdvisor`): Sanitize untrusted text (job descriptions pasted by users) before including in prompts. Detect and neutralize injection attempts.
3. **Grounded-only** (`GroundingValidator`): Resume tailoring output must reference only experience entries in the user's corpus. Never allow invented/hallucinated experience.
4. **JSON Validation** (`JsonSchemaValidator`): Validate every LLM JSON response against its registered schema before returning to caller. Retry once on schema failure; throw `LlmOutputException` if still invalid.

---

## CRITICAL — Privacy Rules

- **Per-user data isolation**: Every repository query that touches user data must include `userId` as a filter. No cross-user data leakage.
- **Only `company_interview_intel` is shared** across users — and only after passing anonymization.
- **Anonymization before write**: Call `AnonymizationService.anonymize()` before inserting into `company_interview_intel`. See `.claude/skills/anonymization.md`.
- **Minimum aggregation threshold**: Do not surface intel for a company until at least N=3 (configurable) anonymized entries exist.
- **Never expose other users' `userId`, `applicationId`, email, or name** in any API response.

---

## Testing Rules

- **Integration tests use Testcontainers** — spin up real PostgreSQL + pgvector container. No H2, no mocks for DB.
- **LLM is always mocked in tests** — use Spring AI test stubs or `@MockBean LlmGateway`. Zero real LLM API calls in tests.
- **Tests green before any deploy** — CI enforces this; do not deploy on red.
- **New endpoints ship with tests** — every Controller method needs at least one `@SpringBootTest` integration test.
- **Naming**: Integration tests end in `IntegrationTest`; unit tests end in `Test`.
- **Run**: `./mvnw test`

---

## Deploy Rule

- **Every phase ends with a redeploy** to DigitalOcean.
- **Flyway migrations must be additive and safe** — no DROP COLUMN, no DROP TABLE, no NOT NULL without DEFAULT on existing tables.
- Use `.claude/commands/deploy-checkpoint.md` before every deploy.

---

## Cost Discipline

- LLM budget: ~$25/month.
- Dev/test: use Ollama (free, local) or cheapest OpenRouter model.
- Tests: mock LLM — zero API cost.
- Prod tailoring: strong model only for TAILORING tier; EXTRACTION tier uses cheapest capable model.
- Review `.claude/skills/spring-ai-rag.md` for tier config.

---

## Commands

| Command | Purpose |
|---|---|
| `/spec <phase>` | Generate DRAFT spec for a phase |
| `/implement-spec <file>` | Implement against an APPROVED spec |
| `/new-feature <name>` | Scaffold a feature slice |
| `/add-llm-task <name>` | Scaffold a ChatClient LLM task |
| `/migration <description>` | Create next Flyway migration |
| `/test` | Run Testcontainers suite, report failures |
| `/review` | Self-review diff against CLAUDE.md rules |
| `/deploy-checkpoint` | Pre-deploy checklist + DigitalOcean steps |

---

## Definition of Done

A phase is done when:
- [ ] Spec is APPROVED (not DRAFT)
- [ ] All spec features are implemented
- [ ] All tests pass (`./mvnw test` green)
- [ ] `/review` passes all checklist items
- [ ] CLAUDE.md updated with new current phase
- [ ] Deployed to DigitalOcean and smoke-tested

## Git commit policy
- Never add a `Co-Authored-By: Claude` (or any Anthropic-related) trailer to commit
  messages. Commit messages should list only human authors.