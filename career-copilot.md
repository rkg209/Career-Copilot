# Career Copilot — Master Project Document (Spec-Driven Development with Claude Code)

> **What this document is.** The single source of truth for the Career Copilot project. It merges the two framings from the original portfolio plans (the *AI/ML-application* view and the *SDE / full-stack* view) into one product, records every key decision we have locked, and lays out the complete Claude Code spec-driven-development (SDD) setup: what goes in `CLAUDE.md`, which skills, slash commands, hooks, sub-agents, MCP servers, and plugins to use, plus the ordered list of specs to build.
>
> **What this document is *not*.** It is intentionally high-level on implementation. Each phase below gets its own detailed technical spec (written later, using the `/spec` workflow described in Part B) before any code is written. This doc tells you *what to build and in what order*, and *how the Claude Code environment is configured to build it*.
>
> **Working style.** Solo developer, weekends, ~$25 LLM budget, GitHub Student Developer Pack for infra, Claude Code as the primary build tool. Thin end-to-end slice first, then deepen. **Deploy at every checkpoint, not once at the end.**

---

## Part A — The Product

### A.1 One-paragraph description
Career Copilot is a deployed, multi-user web application that runs a job search end to end. It **scrapes/ingests job postings** into a browsable portal; lets a user enter their **real experience once**; and for any posting the user chooses, it **tailors a resume (and optionally a cover letter)** to that job using the user's real experience via RAG, renders it through a **user-selected LaTeX template** into a PDF, **scores the resume against the job like an ATS would** and lists what's missing, and **tracks every application and interview**. Interview questions and company-process notes are **pooled into a shared, anonymized public dataset** so every user benefits from what others learned; all other data is private per user. A later phase adds a mock-interview trainer that learns from that pooled data.

### A.2 Two framings, one codebase
The same system serves two resume narratives. Build once; present per audience.

| Axis | Framing | What it foregrounds |
|---|---|---|
| AI/ML application | "An LLM-application engineering project" | RAG, pgvector, Spring AI, guardrails, grounded generation, prompt engineering, evaluation/metrics |
| SDE / full-stack | "A real product, designed-tested-deployed end to end" | Layered architecture, REST, JWT auth, relational + vector modeling, Testcontainers integration tests, CI/CD, live cloud deploy |

Neither framing changes the build. The metrics and the live URL serve both.

### A.3 Locked decisions (the forks we resolved before writing this doc)

1. **Resume rendering = structured JSON → template → compiled PDF (never LLM-generated PDF or raw LaTeX).**
   The LLM emits **only structured content** (tailored bullets, summary, skill ordering) as JSON. A catalog of **4–5 pre-built LaTeX résumé templates** is stored in the repo; the user picks one; a deterministic renderer fills the chosen template with the JSON and compiles to PDF (Tectonic or `pdflatex`). This saves the most tokens, guarantees the LaTeX never breaks, and keeps formatting consistent across every resume. Same pipeline for cover letters.

2. **Model-provider-agnostic via Spring AI `ChatClient`, swappable by config.**
   All LLM calls go through Spring AI's `ChatClient`. We use the **OpenAI starter with an overridable `base-url`**, so we can point at **any OpenAI-compatible endpoint** — OpenRouter (hundreds of models incl. free tiers), Groq, a hosted cheap API, or a local Ollama instance in dev — by changing configuration only, never business logic. Model selection is tiered per task (cheap model for bulk/extraction, stronger model for final tailoring) and is **config-driven**. *(This is "swap the API," not "self-host a model.")*

3. **Job ingestion is the product's entry point (Phase 1), and is pluggable.**
   The flow starts with the portal already populated with jobs. Ingestion is built behind a **pluggable `JobSource` interface**: real job APIs first (e.g. Adzuna / RemoteOK / USAJobs-style endpoints that actually offer APIs) + **manual JD paste**; general HTML scraping is a later, optional adapter behind the same interface (rate-limited, ToS-respecting) so it never blocks the critical path.

4. **Multi-user (target ≥100 users), with incremental deploys at every checkpoint.**
   Proper per-user accounts, JWT auth, and per-user data isolation from Phase 0. The app is **deployable from the first checkpoint** and **redeployed after each phase** (after scraping works, after tailoring works, etc.) rather than deployed once at the end. CI/CD and a repeatable deploy path are first-class from day one.

5. **Mock-interview + spaced-repetition is a later phase**, but **interview-data capture exists early.** From the tracker phase, users can log interview questions and company-process details for applied JDs. The trainer that consumes this data (question generation + SM-2 scheduling + answer evaluation) comes later.

6. **Shared public interview intelligence; everything else private.**
   Interview questions and company-process notes are **pooled across all users into a shared, anonymized, public dataset** ("the more we know about a company's process, the more it helps everyone"). This data is **stripped of personal identifiers** before it enters the shared pool. **All other user data (profile, experience, resume versions, application outcomes) is strictly private and isolated per user.**

7. **Cover letters use the same JSON→template→PDF pipeline, generated on demand only.**
   No cover letter is produced unless the user explicitly requests one for a given application.

8. **ATS scoring uses the publicly-known techniques real ATS/keyword-screeners use.**
   Hybrid approach: parse the resume into structured fields; extract required skills/keywords/qualifications from the JD; compute a **keyword/skill coverage match score** (what real applicant-tracking systems and tools like resume-keyword scanners do); augment with **semantic similarity** (embeddings) to surface near-misses and to power the "missing skills + concrete fixes" explanation. Validated against a small hand-labeled set of JDs.

> **Cross-decision tension we resolved:** running an open-source model locally is free in dev but won't fit a cheap cloud host. Decision #2 settles it — code is provider-agnostic; **dev can use local/free models**, while the **deployed app calls a cheap or free hosted API via OpenRouter**, keeping the $25 budget intact and the live demo viable. (DigitalOcean credit pays for compute + DB but **not** for third-party AI APIs, so LLM spend stays separate and small.)

### A.4 Final tech stack
- **Language/runtime:** Java 21 (virtual threads where useful).
- **Backend:** Spring Boot 3.5+, Spring Web, Spring Security (JWT), Spring Data JPA / Hibernate, **Spring AI 1.1.x** (`ChatClient`, Advisors for RAG + memory, guardrail advisors).
- **Data:** PostgreSQL + **pgvector** (one database does double duty — exact records *and* semantic search over experience; no separate vector service). Flyway for migrations.
- **LLM access:** Spring AI OpenAI starter pointed at an **OpenAI-compatible base-url** (OpenRouter default in prod; Ollama/local optional in dev). Tiered, config-driven model selection. Prompt/response caching for cost control.
- **Resume/cover-letter rendering:** stored LaTeX template catalog (4–5) + Tectonic/`pdflatex` compile step. LLM emits JSON only.
- **Frontend:** React + TypeScript.
- **Testing:** JUnit 5, Testcontainers (real Postgres + pgvector in integration tests), guardrail/grounding tests, optional load test for latency.
- **API docs:** OpenAPI/Swagger.
- **Packaging/CI:** Docker, GitHub Actions.
- **Hosting:** DigitalOcean (App Platform / Droplet + Managed Postgres w/ pgvector) on the $200 student credit; Namecheap free `.me` domain for the live URL. (Heroku student credit, Azure for Students, or Northflank are documented fallbacks — all support pgvector Postgres.)

### A.5 Data model — the privacy split (high level)
Two clearly separated zones:

- **Private (per-user, isolated, never shared):** `users`, `candidate_profile`, `experience_entries` (+ embeddings in pgvector), `resume_versions`, `cover_letters`, `applications` (stage, outcome, dates), each user's own interview answers/notes.
- **Shared (public, anonymized, pooled across users):** `company_interview_intel` — questions asked, round structure, process timeline, difficulty, etc., **with all personal identifiers removed** at write time. A user contributes by logging their interview; the pooled, de-identified record is what becomes public.

> **Guardrail to spec explicitly:** the anonymization step (what fields are stripped/aggregated, how free-text notes are scrubbed of PII) is a dedicated, tested component — not an afterthought. Pooling other people's data carries privacy obligations; treat the de-identification path as security-critical.

### A.6 Resume tailoring pipeline (high level)
```
User picks a JD  ─┐
                  ▼
Parse JD (skills, responsibilities, keywords)
                  ▼
RAG: retrieve user's most relevant experience  ◄── pgvector (their private corpus)
                  ▼
LLM (ChatClient, tiered model) → STRUCTURED JSON only   ── Guardrails: grounded-only (no invented
   {summary, tailored_bullets, skills_order, ...}           experience), PII redaction, JD-injection check
                  ▼
User-selected LaTeX template + JSON → Tectonic/pdflatex → PDF
                  ▼
ATS gap scorer (keyword/skill coverage + semantic) → "missing: K8s, RLHF…" + concrete fixes
                  ▼
Save resume_version; attach to application; (optional) generate cover letter via same pipeline
```

### A.7 Deployment plan
- **Primary:** DigitalOcean — App Platform (or a Droplet running the Docker image) for the Spring Boot service + a Managed PostgreSQL cluster with `CREATE EXTENSION vector;`. Funded by the $200 / 12-month student credit.
- **Domain/TLS:** Namecheap free `.me` domain from the student pack.
- **LLM:** OpenRouter (or other OpenAI-compatible) API key in env; **not** billed to DO credit. Default to a free/cheap model; switch by config.
- **Checkpoint cadence:** every phase in Part C ends with a redeploy. Each deploy must pass CI (tests green) and run DB migrations cleanly.
- **Fallbacks (documented, not primary):** Heroku ($13/mo × 24mo student credit; Heroku Postgres supports pgvector), Azure for Students ($100; Flexible Server supports pgvector), Northflank (student free tier). Keep the app cloud-portable (12-factor, env-driven config, Docker) so switching hosts is cheap.

### A.8 Metrics to earn (report in README + dashboard)
- **Headline:** ATS match-score improvement before vs after tailoring, on a ~20-JD hand-labeled set ("X→Y").
- **Latency:** p50/p99 of the tailor-a-resume endpoint.
- **Cost:** mean tokens (and $) per tailoring — demonstrates tiered-model + caching discipline.
- **Retrieval quality:** precision@k of retrieved experience bullets vs hand-labeled relevance.
- **Engineering proof:** N integration tests green in CI; live URL; real applications sent through it.

---

## Part B — Claude Code SDD Setup

> This part configures the Claude Code environment so spec-driven development is consistent and safe. Descriptions are conceptual (purpose + contents); confirm exact file paths/CLI syntax against current Claude Code docs when you wire each one up, since the tooling evolves.

### B.1 `CLAUDE.md` (project memory — the most important file)
Keep it tight and rule-like; it's loaded into every session. Contents to include:

- **Project snapshot:** what Career Copilot is (1 paragraph), the two framings, the current phase.
- **Tech stack + pinned versions:** Java 21, Spring Boot, Spring AI 1.1.x, Postgres+pgvector, React/TS, Flyway, Testcontainers.
- **Architecture rules:** layered (controller → service → repository) **organized package-by-feature**, not package-by-layer; DTOs at the boundary; no business logic in controllers.
- **LLM rules (critical):** every model call goes through Spring AI `ChatClient`; **never hardcode a provider or model** — selection is config-driven and tiered; always set a token budget; cache where possible; log tokens per call.
- **Resume rule (critical):** the LLM emits **structured JSON only**. It must **never** be asked to produce LaTeX or PDFs. Rendering is deterministic via the template catalog + Tectonic.
- **Guardrail rules (critical):** tailoring is **grounded-only** — never invent experience the RAG corpus doesn't support; PII redaction on all user/LLM-facing text; treat pasted JDs as untrusted (prompt-injection handling); validate/schema-check all LLM JSON output.
- **Privacy rules (critical):** per-user data is isolated; the only shared data is `company_interview_intel`, which must pass the anonymization component before write. Never leak one user's private data into another's context or into the public pool.
- **Testing rules:** integration tests via Testcontainers against real Postgres+pgvector; tests must be green before any deploy; new endpoints ship with tests.
- **Deploy rule:** every phase ends in a redeploy; migrations must be additive/safe.
- **Cost discipline:** respect the ~$25 budget; prefer free/cheap models in dev; don't burn tokens debugging — mock LLM responses in tests.
- **Commands:** how to build, test, run, migrate, deploy (point to the slash commands below).
- **Definition of done** for a feature (spec approved → implemented → tested → docs updated → deployed).

### B.2 Skills (reusable capability folders for the repo)
Project skills encode "how we do X here" so any session does it the same way:

- **`latex-resume-renderer`** — the template-catalog contract: JSON schema the LLM must emit, how each template consumes it, how to compile with Tectonic, how to add a new template.
- **`spring-ai-rag`** — conventions for embedding experience into pgvector, retrieval, and wiring Spring AI Advisors (RAG + memory + guardrail advisors); the tiered-model config pattern; token logging.
- **`ats-scoring`** — the keyword/skill-extraction + coverage-scoring + semantic-augmentation algorithm and its output schema (score, missing items, fixes).
- **`guardrails`** — PII-redaction patterns, prompt-injection handling for pasted JDs, JSON-output validation, and the grounded-only check.
- **`anonymization`** — the de-identification rules for `company_interview_intel` (fields stripped, free-text scrubbing, aggregation thresholds).
- **`jobsource-adapter`** — how to implement a new `JobSource` (rate limits, ToS, normalization to the internal JD schema).
- **`spec-author`** — the repo's spec template + how to write a phase spec consistently (so every `/spec` output matches).

### B.3 Custom slash commands (repeatable workflows)
- **`/spec <phase-or-feature>`** — generate a detailed technical spec from the `spec-author` skill's template for the named phase; output to `/specs`.
- **`/implement-spec <spec-file>`** — implement strictly against an approved spec; stop and ask if the spec is ambiguous.
- **`/new-feature <name>`** — scaffold a feature slice (controller + service + repository + DTOs + tests) package-by-feature.
- **`/add-llm-task <name>`** — scaffold a `ChatClient`-based task with tiered-model config, guardrail advisors, JSON-schema validation, and token logging pre-wired.
- **`/migration <name>`** — create a Flyway migration (incl. pgvector DDL when relevant).
- **`/test`** — run the Testcontainers suite and report failures concisely.
- **`/review`** — self-review the diff against `CLAUDE.md` (architecture, LLM, guardrail, privacy rules) + a security checklist.
- **`/deploy-checkpoint`** — run the pre-deploy checklist (tests green, migrations valid, env vars present, no secrets committed) and emit the DigitalOcean deploy steps for the current phase.

### B.4 Hooks (event-triggered automation / guardrails)
- **On file edit (Java) → format + compile check** (e.g. Spotless + `mvn -q compile`) so edits don't drift from style or break the build.
- **Pre-commit / stop hook → fast tests + secret scan**: block the commit if unit tests fail or if anything resembling an API key / `.env` is staged.
- **LLM-rule guard (custom grep hook):** block commits where LLM-prompt code asks for LaTeX/PDF output, or where a provider/model string is hardcoded instead of config-driven — enforces decisions #1 and #2 automatically.
- **DB-safety guard (PreToolUse on bash):** refuse destructive DB commands (`DROP`, unscoped `DELETE`) outside migrations.
- **Privacy guard:** flag edits that read private user tables in code paths that write to the public pool, prompting a manual check against the anonymization rule.

### B.5 Sub-agents (specialized agents with their own context)
- **`spec-architect`** — turns a Part C phase into a full technical spec; owns the `/spec` flow.
- **`backend-implementer`** — Spring Boot / JPA / Spring AI implementation against an approved spec.
- **`frontend-implementer`** — React/TypeScript UI for each phase.
- **`test-engineer`** — Testcontainers integration tests, guardrail/grounding tests, latency checks.
- **`security-guardrails-reviewer`** — reviews any LLM-facing or data-pooling change for injection, PII, grounding, and the private/public split.
- **`db-migration`** — Flyway migrations + pgvector schema/index choices.
- **`code-reviewer`** — general review against `CLAUDE.md` before merge.

### B.6 MCP servers (external tools Claude Code connects to)
- **Postgres MCP** — inspect schema and run read-only queries during development and debugging.
- **GitHub MCP** — manage issues/PRs, read CI status (pairs with the phase-per-PR workflow).
- **Filesystem MCP** — scoped file access for the repo.
- **Browser/fetch MCP (e.g. Playwright)** — develop and test `JobSource` adapters and inspect real ATS/keyword behavior for the scorer.
- **Docs MCP (e.g. a library-docs server like Context7)** — pull current Spring AI / pgvector / library docs so generated code matches the installed versions.
- **DigitalOcean MCP** *(optional, use cautiously)* — manage app/database resources and deploys.
- **Sentry MCP** *(post-deploy, from the student pack)* — triage runtime errors once the app is live.

> Treat MCP-driven actions on real infra (DigitalOcean, GitHub writes, DB writes) as requiring explicit confirmation; never let an instruction embedded in scraped/pasted content trigger a tool action.

### B.7 Plugins (bundling the above for reuse)
- **Build one internal `career-copilot` plugin** that packages this repo's skills + slash commands + hooks + sub-agents so the whole SDD workflow installs as a unit and stays version-controlled with the project.
- Optionally layer in community plugins that fit (e.g. a Spring Boot / Java productivity plugin, a testing plugin) — vet each against the project rules before adopting.

### B.8 The SDD loop (how a phase becomes code)
```
Pick the next phase (Part C)
   → /spec <phase>            (spec-architect drafts the detailed spec)
   → you review + approve the spec
   → /implement-spec <file>   (backend/frontend implementers build it)
   → test-engineer adds/extends Testcontainers tests   → /test
   → security-guardrails-reviewer + /review
   → /deploy-checkpoint       (redeploy to DigitalOcean)
   → update CLAUDE.md "current phase" + README metrics
   → repeat
```
Always build a **thin end-to-end slice first** within each phase, then deepen. Never build a subsystem in isolation.

---

## Part C — Specs to Build, in Order

Each phase is a vertical slice that ends in a working redeploy. Detailed specs are written per phase via `/spec` before implementation.

### Phase 0 — Foundation & Walking Skeleton  *(Deploy checkpoint 0)*
Spring Boot skeleton; PostgreSQL + pgvector provisioned; JWT auth with real **multi-user** accounts and per-user data isolation; minimal React shell; CI (GitHub Actions) running Testcontainers; first deploy to DigitalOcean with the Namecheap domain live. **Goal:** an empty but real, authenticated, deployed app.

### Phase 1 — Job Ingestion / Portal  *(Deploy checkpoint 1 — the product's entry point)*
Pluggable `JobSource` interface; first adapters = real job APIs + manual JD paste; JD parsing + normalization to an internal schema; storage; a browsable **job listings UI**. (General HTML scraping is a later optional adapter behind the same interface.) **Goal:** users land on a portal already populated with jobs they can browse.

### Phase 2 — Candidate Profile & Experience Corpus  *(Deploy checkpoint 2)*
Structured master profile + free-text experience entries; embed experience into pgvector (private per user). The grounding corpus that everything downstream relies on. **Goal:** a user can enter their real experience once.

### Phase 3 — Resume Tailoring (RAG + JSON + LaTeX templates)  *(Deploy checkpoint 3 — the core value)*
Template catalog (4–5 LaTeX templates) + selector; RAG retrieval of relevant experience; `ChatClient` (tiered model) emitting **structured JSON only**; deterministic LaTeX→PDF render; guardrails (grounded-only, PII redaction, JD-injection handling, JSON validation). Cover-letter generation available **on demand** via the same pipeline. **Goal:** pick a JD → get a tailored, well-formatted resume PDF grounded in real experience.

### Phase 4 — ATS Gap Scorer  *(Deploy checkpoint 4)*
JD skill/keyword extraction; keyword/skill **coverage match score** (real-ATS technique) augmented with semantic similarity; "missing skills + concrete fixes" output. Validated on a ~20-JD hand-labeled set (the headline metric). **Goal:** the user sees, before applying, why a resume would or wouldn't pass screening and how to fix it.

### Phase 5 — Application & Interview Tracker  *(Deploy checkpoint 5)*
Per-user application lifecycle (stages, dates, outcomes); **interview logging** (questions asked, round structure, process notes) for applied JDs. Capture only here; the trainer comes later. **Goal:** every application and interview is recorded; rejections become future prep data.

### Phase 6 — Public Company-Intel Aggregation  *(Deploy checkpoint 6)*
The **anonymization component** + the shared `company_interview_intel` pool + a public browse view aggregating de-identified questions/process info across users. Private data stays private; only scrubbed, pooled intel is public. **Goal:** everyone benefits from what everyone learned about each company's process.

### Phase 7 — Mock-Interview Trainer + Spaced Repetition  *(Deploy checkpoint 7 — later)*
Generate questions from the user's own logs + the public pool + role patterns; evaluate answers; schedule weak areas via SM-2 spaced repetition. **Goal:** turn the captured data into active interview practice.

### Phase 8 — Hardening, Metrics & Polish  *(Final)*
Wire up the full metric set (ATS improvement, latency p50/p99, tokens/tailoring, retrieval precision@k); observability (incl. Sentry); guardrail/anonymization test coverage; OpenAPI docs; architecture diagram; demo. **Goal:** a defensible, measured, demo-ready portfolio piece.

### Cross-cutting (built into the phases, not separate)
Guardrails (Phase 3 onward), privacy/anonymization (Phase 6 is the focus, but the rule applies throughout), observability + cost logging (from Phase 3), and CI/CD + redeploy (from Phase 0).

---

## Appendix — Items to confirm when writing each detailed spec
- Exact job APIs to use first (verify each still offers a free, ToS-compatible API at build time: Adzuna, RemoteOK, USAJobs, etc.).
- Embedding model + dimension (must match the pgvector column; pick one and standardize).
- Default prod model on OpenRouter (a free/cheap model) and the tiered split (bulk vs final tailoring).
- DigitalOcean App Platform vs Droplet for the Spring service (cost vs control on the $200 credit).
- Anonymization policy details for the public pool (fields stripped, free-text scrubbing, minimum-aggregation thresholds before a record becomes public).
- The ~20-JD labeled set used to validate the ATS scorer (build it incrementally as you apply).
- Current Claude Code syntax/paths for skills, slash commands, hooks, sub-agents, and plugins (confirm against current docs when wiring the internal plugin).
