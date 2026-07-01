# Career Copilot — Requirements Document

**Document version:** 1.0
**Status:** Baseline
**Source:** project.md (Master Project Document)

---

## 1. Business Goals

| ID | Goal |
|----|------|
| BG-1 | Deliver a publicly deployed, multi-user web application that automates the end-to-end job-search workflow: job discovery → resume tailoring → ATS gap analysis → application tracking → interview preparation. |
| BG-2 | Demonstrate measurable, quantified value: ATS match-score improvement before vs. after tailoring on a ≥20-JD hand-labeled benchmark set. |
| BG-3 | Serve as a defensible portfolio artifact under two simultaneous framings — an AI/ML-engineering project (RAG, pgvector, Spring AI, guardrails, grounded generation, prompt engineering, evaluation) and a full-stack SDE project (layered architecture, REST, JWT auth, relational + vector data modeling, Testcontainers integration tests, CI/CD, live cloud deploy). |
| BG-4 | Operate within a ~$25 total LLM spend budget and a $200 DigitalOcean student credit for compute and database, with no cost overruns that would terminate the live demo. |
| BG-5 | Create a network-effect data asset: a shared, anonymized, public pool of company interview intelligence that grows in value as the user base grows. |
| BG-6 | Deploy incrementally — a working, redeployed application exists after every phase — so the project is never "almost done" and always has a live URL. |

---

## 2. Stakeholders

| ID | Stakeholder | Interest / Concern |
|----|-------------|-------------------|
| SH-1 | Solo developer (project owner) | Builds, maintains, and presents the system; primary decision-maker on scope, cost, and schedule. |
| SH-2 | Portfolio reviewers (technical interviewers, hiring managers) | Evaluate the system as evidence of AI/ML-application engineering and full-stack SDE competence; need a live URL, a README with metrics, and a clear architecture story. |
| SH-3 | End users (job seekers) | Use the product to find jobs, tailor resumes, track applications, and prepare for interviews; require data privacy and accurate, grounded output. |
| SH-4 | Contributing users (interview data donors) | Log interview questions and process notes; expect their personal identifiers to be stripped before data enters the public pool. |
| SH-5 | Public pool consumers | Browse anonymized company interview intelligence contributed by other users; expect the data to be accurate and de-identified. |
| SH-6 | Infrastructure providers (DigitalOcean, Namecheap, OpenRouter) | Provide compute, domain, and LLM API services within the agreed credit/cost limits. |

---

## 3. Users / Personas

| ID | Persona | Description | Primary Goals |
|----|---------|-------------|---------------|
| P-1 | Active Job Seeker | A software professional actively applying to multiple roles simultaneously. Has a rich work history but struggles to tailor resumes quickly and consistently. | Browse job postings; generate tailored resume PDFs; understand ATS gaps; track applications. |
| P-2 | Interview Preparer | A user who has received interview invitations and wants to prepare using real, crowd-sourced company process data. | Access public company interview intel; log their own interview experience; run mock interview sessions (Phase 7). |
| P-3 | Passive Browser | A user exploring the job market without actively applying. | Browse job listings; view public company intel without necessarily creating a full profile. |
| P-4 | Data Contributor | Any authenticated user who logs interview questions and process notes after an interview. Overlaps with P-1 and P-2. | Contribute interview data; benefit from the shared pool in return. |

---

## 4. Functional Requirements

### 4.1 Authentication & Multi-User Accounts

| ID | Requirement |
|----|-------------|
| FR-1 | The system shall support self-service user registration with email and password. |
| FR-2 | The system shall authenticate users via JWT tokens; all protected endpoints shall reject requests with missing or invalid tokens. |
| FR-3 | The system shall enforce per-user data isolation: no query, API response, or LLM context shall expose one user's private data to another user. |
| FR-4 | The system shall support a minimum of 100 concurrent registered users without architectural changes. |
| FR-5 | The system shall provide a logout mechanism that invalidates the user's active session token. |

### 4.2 Job Ingestion & Portal

| ID | Requirement |
|----|-------------|
| FR-6 | The system shall define a pluggable `JobSource` interface that normalizes job postings from any source into a single internal Job Description (JD) schema. |
| FR-7 | The system shall implement at least one adapter for a real, free, ToS-compatible job API (e.g., Adzuna, RemoteOK, or USAJobs) as the first concrete `JobSource`. |
| FR-8 | The system shall implement a manual JD paste adapter conforming to the `JobSource` interface, allowing any user to submit a raw job description text. |
| FR-9 | The system shall parse and normalize ingested job postings into the internal JD schema, extracting at minimum: job title, company name, location, employment type, required skills, responsibilities, and full description text. |
| FR-10 | The system shall store normalized job postings in the relational database and make them browsable via a paginated job listings UI. |
| FR-11 | The system shall support keyword and filter-based search over the job listings (e.g., by title, company, location, skills). |
| FR-12 | General HTML scraping shall be implemented as an optional, later `JobSource` adapter behind the same interface and shall not block any earlier phase. |

### 4.3 Candidate Profile & Experience Corpus

| ID | Requirement |
|----|-------------|
| FR-13 | The system shall allow each authenticated user to create and maintain a structured master candidate profile containing at minimum: contact information, education history, certifications, and skills. |
| FR-14 | The system shall allow each authenticated user to create, read, update, and delete free-text experience entries (e.g., job roles, projects, achievements). |
| FR-15 | The system shall embed each experience entry into a pgvector column using a configured embedding model, storing the resulting vector privately per user. |
| FR-16 | The embedding model and vector dimension shall be fixed by configuration at project initialization; the pgvector column definition shall match this dimension exactly. |
| FR-17 | The system shall re-embed an experience entry whenever its text content is updated. |

### 4.4 Resume Tailoring Pipeline

| ID | Requirement |
|----|-------------|
| FR-18 | The system shall provide a resume tailoring workflow triggered when an authenticated user selects a job posting. |
| FR-19 | The tailoring pipeline shall retrieve the user's most semantically relevant experience entries from pgvector using the JD as the query (RAG retrieval). |
| FR-20 | The tailoring pipeline shall invoke an LLM via Spring AI `ChatClient` to produce **structured JSON only** — containing at minimum: tailored summary, tailored bullet points, and ordered skills list — and shall never request LaTeX, PDF, or any rendered output from the LLM. |
| FR-21 | The system shall maintain a catalog of 4–5 pre-built LaTeX résumé templates stored in the repository. |
| FR-22 | The system shall allow the user to select one template from the catalog before or during the tailoring workflow. |
| FR-23 | The system shall deterministically render the LLM-produced JSON into the user-selected LaTeX template and compile it to a PDF using Tectonic or `pdflatex`. |
| FR-24 | The system shall validate the LLM JSON output against a defined schema before rendering; invalid output shall be rejected and an error returned rather than silently producing a malformed resume. |
| FR-25 | The system shall store each generated resume as a versioned `resume_version` record associated with the user and the source JD. |
| FR-26 | The system shall make the generated PDF available for download by the owning user. |
| FR-27 | The tailoring guardrail shall enforce grounded-only generation: the LLM output shall reference only experience present in the retrieved RAG corpus; the system shall detect and reject output that introduces experience not grounded in the user's corpus. |
| FR-28 | The tailoring pipeline shall apply PII redaction to all text passed to the LLM and to all LLM responses before storage. |
| FR-29 | The tailoring pipeline shall treat pasted JD text as untrusted input and apply prompt-injection detection and sanitization before including it in any LLM prompt. |

### 4.5 Cover Letter Generation

| ID | Requirement |
|----|-------------|
| FR-30 | The system shall generate a cover letter only when the user explicitly requests one for a specific application. |
| FR-31 | Cover letter generation shall use the same JSON → LaTeX template → PDF pipeline as resume tailoring (FR-20 through FR-23). |
| FR-32 | The system shall store each generated cover letter as a versioned record associated with the user and the source JD. |

### 4.6 ATS Gap Scorer

| ID | Requirement |
|----|-------------|
| FR-33 | The system shall extract required skills, keywords, and qualifications from a JD using a defined parsing algorithm. |
| FR-34 | The system shall compute a keyword/skill coverage match score between a generated (or uploaded) resume and the source JD, using techniques representative of real applicant-tracking systems. |
| FR-35 | The system shall augment the keyword coverage score with semantic similarity (embeddings) to surface near-miss skills and synonyms. |
| FR-36 | The system shall produce a structured ATS gap report containing at minimum: overall match score, list of missing required skills/keywords, list of near-miss items, and a list of concrete, actionable fixes. |
| FR-37 | The ATS gap report shall be displayed to the user before they submit an application. |
| FR-38 | The ATS scorer shall be validated against a hand-labeled set of ≥20 JD/resume pairs; validation results shall be reported in the README as the headline metric. |

### 4.7 Application & Interview Tracker

| ID | Requirement |
|----|-------------|
| FR-39 | The system shall allow each authenticated user to create an application record linking their user account, a job posting, and a resume version. |
| FR-40 | Each application record shall track at minimum: application date, current stage (e.g., applied, phone screen, technical interview, offer, rejected, withdrawn), and outcome. |
| FR-41 | The system shall allow users to update the stage and outcome of an application record over time. |
| FR-42 | The system shall allow users to log interview events against an application record, capturing at minimum: interview round type, date, questions asked, and process/format notes. |
| FR-43 | Interview log entries shall be stored privately per user in the `applications` data zone and shall not be automatically shared. |

### 4.8 Public Company Interview Intelligence

| ID | Requirement |
|----|-------------|
| FR-44 | The system shall provide an anonymization component that transforms a user's private interview log entry into a de-identified record before it enters the shared pool. |
| FR-45 | The anonymization component shall strip all personal identifiers from interview log entries at write time; the specific fields stripped and the free-text scrubbing rules shall be defined in the `anonymization` skill and treated as security-critical. |
| FR-46 | The anonymization component shall enforce a minimum-aggregation threshold: a company's interview data shall not appear in the public pool until a configurable minimum number of de-identified contributions exist for that company, preventing re-identification of early contributors. |
| FR-47 | The system shall maintain a shared `company_interview_intel` table in the database containing only de-identified, pooled records. |
| FR-48 | The system shall provide a public browse view of the `company_interview_intel` pool, accessible without authentication, showing aggregated questions, round structures, process timelines, and difficulty ratings per company. |
| FR-49 | A user shall be able to explicitly opt their interview log entry into the public pool; the anonymization component (FR-44 through FR-46) shall execute at that point. |

### 4.9 Mock-Interview Trainer (Phase 7 — later)

| ID | Requirement |
|----|-------------|
| FR-50 | The system shall generate interview practice questions drawn from the user's own interview logs, the public company intel pool, and role-pattern data. |
| FR-51 | The system shall evaluate user-submitted answers to practice questions and provide structured feedback. |
| FR-52 | The system shall schedule question repetition for weak areas using the SM-2 spaced-repetition algorithm. |

### 4.10 Developer & Operations

| ID | Requirement |
|----|-------------|
| FR-53 | The system shall expose all backend endpoints via an OpenAPI/Swagger specification, auto-generated and served at a documented path. |
| FR-54 | The system shall apply Flyway database migrations on startup; all migrations shall be additive and safe (no destructive schema changes in a migration that cannot be rolled back). |
| FR-55 | The system shall log token usage (prompt tokens, completion tokens, total tokens, and estimated cost) for every LLM call. |
| FR-56 | The system shall support a mock-LLM mode in tests so that Testcontainers integration tests never make real LLM API calls. |
| FR-57 | The CI pipeline shall run the full Testcontainers integration test suite on every push and shall block merge if any test fails. |
| FR-58 | The system shall collect and expose the following operational metrics: ATS match-score improvement (pre/post tailoring), p50 and p99 latency of the tailor-a-resume endpoint, mean tokens and mean cost per tailoring call, and retrieval precision@k for experience bullet retrieval. |

---

## 5. Non-Functional Requirements

### 5.1 Performance

| ID | Requirement |
|----|-------------|
| NFR-1 | The tailor-a-resume endpoint shall achieve a p50 latency of ≤30 seconds and a p99 latency of ≤60 seconds under single-user load, excluding PDF compilation time. |
| NFR-2 | PDF compilation (LaTeX → PDF) shall complete within 15 seconds for a standard single-page resume on the production host. |
| NFR-3 | Job listing pages shall load (server response) within 2 seconds for paginated queries of up to 100 results. |
| NFR-4 | pgvector similarity searches over a user's experience corpus shall complete within 500 ms for corpora of up to 500 entries. |

### 5.2 Scalability

| ID | Requirement |
|----|-------------|
| NFR-5 | The application shall support ≥100 registered users without architectural changes to the data model or deployment topology. |
| NFR-6 | The `JobSource` adapter system shall be extensible: adding a new job source shall require implementing one interface and registering one adapter, with no changes to the core ingestion or storage logic. |

### 5.3 Security

| ID | Requirement |
|----|-------------|
| NFR-7 | All HTTP communication between client and server shall be encrypted via TLS (HTTPS). |
| NFR-8 | JWT tokens shall be signed with a secret stored only in environment variables; the secret shall never appear in source code, configuration files committed to the repository, or logs. |
| NFR-9 | All API endpoints that access user-specific data shall verify that the authenticated user is the owner of the requested resource; cross-user data access shall return HTTP 403. |
| NFR-10 | Pasted JD text and any other user-supplied free text included in LLM prompts shall be sanitized for prompt-injection patterns before use. |
| NFR-11 | No API key, database password, or other secret shall be committed to the repository; a pre-commit hook shall scan staged files and block the commit if a secret pattern is detected. |
| NFR-12 | The anonymization component (FR-44 through FR-46) shall be treated as security-critical code: it shall have dedicated unit and integration tests, and changes to it shall require explicit review against the anonymization skill specification. |
| NFR-13 | Destructive database commands (`DROP TABLE`, unscoped `DELETE`, etc.) shall be prohibited outside of Flyway migration files; a development hook shall block such commands from being executed interactively against the database. |

### 5.4 Privacy

| ID | Requirement |
|----|-------------|
| NFR-14 | Private user data (profile, experience entries, resume versions, cover letters, application records, raw interview logs) shall never be written to the `company_interview_intel` shared table or any other shared data store. |
| NFR-15 | Private user data shall never appear in LLM prompts constructed for a different user. |
| NFR-16 | The de-identification rules for the public pool shall be documented in the `anonymization` skill and shall specify exactly which fields are stripped, how free-text is scrubbed, and what the minimum-aggregation threshold is. |

### 5.5 Reliability & Availability

| ID | Requirement |
|----|-------------|
| NFR-17 | The application shall remain available (no planned downtime) during a rolling redeploy triggered by a new phase deployment, or shall display a maintenance page for no longer than 5 minutes. |
| NFR-18 | A failed LLM API call shall return a structured error to the user within 10 seconds; the system shall not hang indefinitely waiting for an LLM response. |
| NFR-19 | A failed PDF compilation shall return a structured error to the user; the system shall not silently produce a corrupt or empty PDF. |

### 5.6 Maintainability & Code Quality

| ID | Requirement |
|----|-------------|
| NFR-20 | The backend codebase shall be organized package-by-feature (not package-by-layer); each feature package shall contain its own controller, service, repository, and DTOs. |
| NFR-21 | No business logic shall reside in controller classes; controllers shall delegate to service classes. |
| NFR-22 | All LLM calls shall go through Spring AI `ChatClient`; no code shall call an LLM provider SDK directly. |
| NFR-23 | No provider name, model name, or model identifier shall be hardcoded in business logic; all model selection shall be driven by application configuration. |
| NFR-24 | The codebase shall pass Spotless formatting checks on every build; the CI pipeline shall fail if formatting violations are present. |
| NFR-25 | Every new API endpoint shall ship with at least one Testcontainers integration test covering the happy path and at least one error path. |
| NFR-26 | The `CLAUDE.md` project memory file shall be updated to reflect the current phase and any locked decisions after each phase is completed. |

### 5.7 Cost Discipline

| ID | Requirement |
|----|-------------|
| NFR-27 | Total LLM API spend across the lifetime of the project shall not exceed $25 USD. |
| NFR-28 | Integration and unit tests shall use mocked LLM responses; no test shall make a real LLM API call. |
| NFR-29 | The system shall apply prompt/response caching where the LLM provider supports it, to reduce redundant token spend. |
| NFR-30 | Model selection shall be tiered by task: a cheaper or free model shall be used for bulk/extraction tasks; a stronger model shall be used only for final resume tailoring; the tier assignment shall be defined in configuration. |
| NFR-31 | The system shall log mean tokens and mean estimated cost per tailoring call (FR-55, FR-58) so cost trends are visible and actionable. |

### 5.8 Observability

| ID | Requirement |
|----|-------------|
| NFR-32 | The application shall integrate with Sentry (or equivalent) for runtime error tracking in the production environment. |
| NFR-33 | The application shall expose the operational metrics defined in FR-58 in a form that can be reported in the README and, optionally, a live dashboard. |
| NFR-34 | Application logs shall include structured fields for request ID, user ID (non-PII token), endpoint, and duration for every API request. |

### 5.9 Accessibility & Usability

| ID | Requirement |
|----|-------------|
| NFR-35 | The React frontend shall meet WCAG 2.1 Level AA for all primary user flows (job browsing, resume tailoring, application tracking). |
| NFR-36 | The application shall display meaningful, user-readable error messages for all failure modes visible to the user; raw stack traces or internal error codes shall not be exposed in the UI. |

---

## 6. Constraints

| ID | Constraint |
|----|------------|
| CON-1 | **LLM output format:** The LLM shall never be prompted to produce LaTeX, PDF, or any rendered document format. It shall produce structured JSON only. This constraint is enforced by a pre-commit hook that scans LLM-prompt code for LaTeX/PDF output requests. |
| CON-2 | **Provider agnosticism:** No LLM provider name, model name, or API key shall appear in business logic or committed configuration. All such values shall be supplied via environment variables or external configuration. This constraint is enforced by a pre-commit hook. |
| CON-3 | **Budget — compute:** Total cloud compute and database spend shall not exceed the $200 DigitalOcean student credit over the project lifetime. |
| CON-4 | **Budget — LLM:** Total LLM API spend shall not exceed $25 USD (see NFR-27). |
| CON-5 | **Solo developer, weekend cadence:** All design and implementation decisions must be executable by one developer working on weekends; scope that requires parallel team effort is out of bounds. |
| CON-6 | **No self-hosted model inference in production:** The production deployment shall call a hosted, OpenAI-compatible API endpoint (e.g., OpenRouter). Running a local model in production is prohibited due to compute cost constraints. Local/Ollama models are permitted in development only. |
| CON-7 | **Additive-only database migrations:** Flyway migrations shall never drop tables, drop columns, or execute unscoped deletes. Schema changes that remove data require a multi-step migration strategy. |
| CON-8 | **ToS compliance for job sources:** Any `JobSource` adapter that fetches data from a third-party service shall comply with that service's Terms of Service and rate limits. General HTML scraping adapters shall not be deployed until ToS compliance is confirmed. |
| CON-9 | **Incremental deployment:** Every phase defined in Part C of the project specification shall end with a successful redeploy to the production environment. No phase may be considered complete without a passing CI run and a live redeploy. |
| CON-10 | **Embedding model lock-in:** The embedding model and vector dimension shall be fixed at project initialization. Changing the embedding model after experience entries have been stored requires a full re-embedding migration; this shall be treated as a breaking change. |
| CON-11 | **Java 21 / Spring Boot 3.5+ / Spring AI 1.1.x:** The backend shall use these specific versions. Upgrades to major versions require an explicit decision and a spec update. |

---

## 7. Technologies

### 7.1 Backend

| Component | Technology | Notes |
|-----------|------------|-------|
| Language | Java 21 | Virtual threads used where beneficial (e.g., blocking I/O in LLM calls, PDF compilation). |
| Framework | Spring Boot 3.5+ | Spring Web (REST), Spring Security (JWT), Spring Data JPA / Hibernate. |
| AI / LLM integration | Spring AI 1.1.x | `ChatClient`, RAG Advisors, memory Advisors, guardrail Advisors. |
| LLM provider | OpenAI-compatible API via Spring AI OpenAI starter | `base-url` overridable by config; OpenRouter in production; Ollama optional in development. |
| Database ORM | Spring Data JPA / Hibernate | |
| Database migrations | Flyway | |
| API documentation | OpenAPI 3 / Springdoc Swagger UI | Auto-generated; served at a documented path. |

### 7.2 Data

| Component | Technology | Notes |
|-----------|------------|-------|
| Primary database | PostgreSQL (managed, DigitalOcean) | Single database for relational records and vector data. |
| Vector extension | pgvector | `CREATE EXTENSION vector;` on the managed Postgres cluster. Used for experience-entry embeddings and semantic search. |
| Embedding model | TBD at spec time | Fixed dimension; must match pgvector column definition. Confirm model and dimension before Phase 2 implementation. |

### 7.3 Resume & Cover Letter Rendering

| Component | Technology | Notes |
|-----------|------------|-------|
| Template format | LaTeX | 4–5 templates stored in the repository. |
| PDF compiler | Tectonic or `pdflatex` | Deterministic; invoked server-side. |
| Template catalog | Repository-managed files | LLM JSON is injected into the selected template; no LLM involvement in rendering. |

### 7.4 Frontend

| Component | Technology |
|-----------|------------|
| Framework | React |
| Language | TypeScript |

### 7.5 Testing

| Component | Technology | Notes |
|-----------|------------|-------|
| Unit testing | JUnit 5 | |
| Integration testing | Testcontainers | Real PostgreSQL + pgvector containers; no real LLM calls in tests. |
| Guardrail / grounding tests | JUnit 5 + custom assertions | Validate grounded-only output, PII redaction, JSON schema compliance. |
| Load / latency testing | Optional (e.g., Gatling or k6) | For p50/p99 latency metric collection. |

### 7.6 DevOps & Infrastructure

| Component | Technology | Notes |
|-----------|------------|-------|
| Containerization | Docker | Application packaged as a Docker image. |
| CI/CD | GitHub Actions | Runs Spotless, compile, Testcontainers suite, secret scan on every push; blocks merge on failure. |
| Primary hosting | DigitalOcean App Platform or Droplet | Funded by $200 student credit. |
| Database hosting | DigitalOcean Managed PostgreSQL | pgvector extension enabled. |
| Domain & TLS | Namecheap `.me` domain (student pack) + DigitalOcean TLS termination | |
| Error tracking | Sentry | Integrated post-deploy; available via student pack. |
| Fallback hosts (documented) | Heroku (student credit), Azure for Students, Northflank | App must be 12-factor and Docker-portable to enable host switching. |

### 7.7 Development Tooling (Claude Code SDD)

| Component | Technology / Artifact |
|-----------|----------------------|
| Project memory | `CLAUDE.md` |
| Reusable capability skills | `latex-resume-renderer`, `spring-ai-rag`, `ats-scoring`, `guardrails`, `anonymization`, `jobsource-adapter`, `spec-author` |
| Custom slash commands | `/spec`, `/implement-spec`, `/new-feature`, `/add-llm-task`, `/migration`, `/test`, `/review`, `/deploy-checkpoint` |
| Hooks | Java-edit format+compile check; pre-commit fast tests + secret scan; LLM-rule guard (no LaTeX/PDF output, no hardcoded model); DB-safety guard; privacy guard |
| Sub-agents | `spec-architect`, `backend-implementer`, `frontend-implementer`, `test-engineer`, `security-guardrails-reviewer`, `db-migration`, `code-reviewer` |
| MCP servers | Postgres MCP, GitHub MCP, Filesystem MCP, Browser/Playwright MCP, Docs MCP (e.g., Context7), DigitalOcean MCP (optional), Sentry MCP |
| Internal plugin | `career-copilot` plugin bundling all skills, commands, hooks, and sub-agents |

---

## 8. Deployment Requirements

| ID | Requirement |
|----|-------------|
| DR-1 | The application shall be packaged as a Docker image and deployable to any OCI-compatible container host without code changes. |
| DR-2 | All environment-specific configuration (database URL, LLM API key, JWT secret, embedding model endpoint, model names) shall be supplied exclusively via environment variables; no environment-specific values shall be present in committed files. |
| DR-3 | The production deployment shall use a DigitalOcean Managed PostgreSQL cluster with the `pgvector` extension enabled (`CREATE EXTENSION vector;`). |
| DR-4 | The application shall apply Flyway migrations automatically on startup; a failed migration shall prevent the application from starting and shall emit a clear error log. |
| DR-5 | The production deployment shall be accessible at a Namecheap `.me` domain with valid TLS; HTTP requests shall be redirected to HTTPS. |
| DR-6 | Every phase completion (Phases 0 through 8) shall conclude with a successful redeploy to the production environment; the redeploy is part of the definition of done for each phase. |
| DR-7 | A redeploy shall pass the following pre-deploy checklist before executing: all CI tests green, all Flyway migrations validated, all required environment variables confirmed present, no secrets detected in the committed diff. |
| DR-8 | The application shall be deployable to the documented fallback hosts (Heroku, Azure for Students, Northflank) by changing environment variables and the container target only; no code changes shall be required to switch hosts. |
| DR-9 | The LLM API endpoint shall be an OpenRouter (or other OpenAI-compatible) hosted service in production; local model inference shall not be used in the production deployment. |
| DR-10 | The CI/CD pipeline (GitHub Actions) shall run on every push to the main branch and on every pull request; it shall execute: Spotless formatting check, Maven compile, full Testcontainers integration test suite, and secret scan. Merge shall be blocked if any step fails. |
| DR-11 | The production deployment topology shall remain within the $200 DigitalOcean student credit for the full project lifetime; the developer shall monitor credit consumption after each phase redeploy. |
| DR-12 | The application shall support zero-downtime or near-zero-downtime redeployment (rolling restart or maintenance page ≤5 minutes, per NFR-17). |

---

*End of requirements.md — version 1.0*