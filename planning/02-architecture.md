# Career Copilot — Architecture Document

**Document version:** 1.0
**Status:** Implementation-Ready Baseline
**Builds on:** `project.md`, `requirements.md`, `project-summary.md`

---

## 1. System Overview

Career Copilot is a multi-user, cloud-deployed web application organized as a single Spring Boot backend serving a React/TypeScript single-page application. The backend exposes a versioned REST API, manages all data in a single PostgreSQL database (with the pgvector extension), and delegates all LLM work to an external OpenAI-compatible API endpoint via Spring AI. PDF rendering is performed server-side by a local LaTeX compiler (Tectonic) invoked as a subprocess. There is no microservices split, no message broker, and no separate vector database — one process, one database, one external LLM endpoint.

The system is organized into eight vertical feature slices that map directly to the phases in the project specification. Each slice owns its own package, its own database tables, and its own API surface. Cross-slice dependencies flow in one direction only: downstream slices depend on upstream ones (e.g., tailoring depends on the experience corpus; the ATS scorer depends on tailoring), never the reverse.

### Guiding Architectural Principles

1. **One process, one database.** No service mesh, no distributed transactions, no eventual consistency problems. Complexity is added only when a concrete constraint forces it.
2. **LLM is a pure function in the pipeline.** It receives a prompt, returns structured JSON, and has no side effects. All state management, persistence, and rendering happen outside the LLM call.
3. **Deterministic rendering.** The path from user experience data to PDF is fully deterministic once the LLM has produced its JSON. The same JSON + the same template always produces the same PDF.
4. **Privacy by structure.** The private/public data split is enforced at the schema level (separate tables, separate access paths), not by application-layer filtering alone.
5. **Config-driven variability.** Everything that might change between environments (model names, API endpoints, feature flags, cost tiers) is an environment variable. No environment-specific logic in code.
6. **Deploy at every checkpoint.** The architecture must support incremental deployment from Phase 0 onward. Nothing is "wired up at the end."

---

## 2. Component Architecture

### 2.1 Top-Level Component Map

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENT TIER                                  │
│                                                                     │
│   React 18 / TypeScript SPA                                         │
│   ├── Auth module (login, register, JWT storage)                    │
│   ├── Job Portal module (browse, search, filter, paste JD)          │
│   ├── Profile & Experience module                                   │
│   ├── Resume Tailoring module (template picker, PDF download)       │
│   ├── ATS Scorer module (gap report display)                        │
│   ├── Application Tracker module (kanban/list, interview log)       │
│   └── Public Intel module (company interview browse — no auth)      │
│                                                                     │
│   Communicates exclusively via HTTPS REST to /api/v1/**             │
└─────────────────────────────────────────────────────────────────────┘
                              │ HTTPS / TLS
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       APPLICATION TIER                              │
│                                                                     │
│   Spring Boot 3.5 / Java 21 — single deployable JAR/container      │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │  Security Layer                                             │   │
│   │  Spring Security filter chain — JWT validation,            │   │
│   │  per-request user context, CORS, HTTPS enforcement         │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│   Feature Packages (package-by-feature):                           │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐    │
│   │  auth        │  │  jobs        │  │  profile             │    │
│   │  controller  │  │  controller  │  │  controller          │    │
│   │  service     │  │  service     │  │  service             │    │
│   │  repository  │  │  repository  │  │  repository          │    │
│   │  dto         │  │  dto         │  │  dto                 │    │
│   └──────────────┘  └──────────────┘  └──────────────────────┘    │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐    │
│   │  tailoring   │  │  scoring     │  │  tracker             │    │
│   │  controller  │  │  controller  │  │  controller          │    │
│   │  service     │  │  service     │  │  service             │    │
│   │  repository  │  │  repository  │  │  repository          │    │
│   │  dto         │  │  dto         │  │  dto                 │    │
│   └──────────────┘  └──────────────┘  └──────────────────────┘    │
│   ┌──────────────┐  ┌──────────────────────────────────────────┐   │
│   │  intel       │  │  shared / cross-cutting                  │   │
│   │  controller  │  │  ├── LlmGateway (Spring AI ChatClient)   │   │
│   │  service     │  │  ├── EmbeddingService                    │   │
│   │  repository  │  │  ├── AnonymizationService                │   │
│   │  dto         │  │  ├── PiiRedactionService                 │   │
│   └──────────────┘  │  ├── PromptInjectionGuard               │   │
│                      │  ├── GroundingValidator                 │   │
│                      │  ├── JsonSchemaValidator                │   │
│                      │  ├── TokenUsageLogger                   │   │
│                      │  └── MetricsCollector                   │   │
│                      └──────────────────────────────────────────┘   │
│                                                                     │
│   Infrastructure Adapters:                                          │
│   ┌──────────────────────┐  ┌───────────────────────────────────┐  │
│   │  JobSource adapters  │  │  LatexRenderingService            │  │
│   │  ├── AdzunaAdapter   │  │  (Tectonic subprocess invocation, │  │
│   │  ├── RemoteOKAdapter │  │   template catalog, JSON inject)  │  │
│   │  └── PasteAdapter    │  └───────────────────────────────────┘  │
│   └──────────────────────┘                                          │
└─────────────────────────────────────────────────────────────────────┘
          │                                    │
          ▼                                    ▼
┌──────────────────────┐          ┌────────────────────────────────┐
│    DATA TIER         │          │    EXTERNAL SERVICES           │
│                      │          │                                │
│  PostgreSQL 16       │          │  OpenAI-compatible LLM API     │
│  + pgvector          │          │  (OpenRouter in prod;          │
│                      │          │   Ollama optional in dev)      │
│  Single database,    │          │                                │
│  two data zones:     │          │  Job Source APIs               │
│  - private (per user)│          │  (Adzuna, RemoteOK, USAJobs)   │
│  - shared (public    │          │                                │
│    intel pool)       │          │  Sentry (error tracking)       │
└──────────────────────┘          └────────────────────────────────┘
```

### 2.2 Feature Package Detail

Each feature package follows an identical internal structure. The pattern is enforced by the `/new-feature` slash command and validated by the `code-reviewer` sub-agent.

```
com.careercopilot.
├── auth/
│   ├── AuthController.java          — REST endpoints only; delegates to service
│   ├── AuthService.java             — registration, login, token issuance
│   ├── UserRepository.java          — Spring Data JPA
│   ├── User.java                    — JPA entity
│   ├── dto/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   └── AuthResponse.java
│   └── JwtTokenProvider.java        — token sign/verify; secret from env
│
├── jobs/
│   ├── JobController.java
│   ├── JobService.java
│   ├── JobRepository.java
│   ├── JobPosting.java              — JPA entity
│   ├── dto/
│   │   ├── JobPostingResponse.java
│   │   ├── JobSearchRequest.java
│   │   └── ManualJdPasteRequest.java
│   └── source/
│       ├── JobSource.java           — interface
│       ├── AdzunaJobSource.java
│       ├── RemoteOKJobSource.java
│       └── ManualPasteJobSource.java
│
├── profile/
│   ├── ProfileController.java
│   ├── ProfileService.java
│   ├── ProfileRepository.java
│   ├── ExperienceRepository.java
│   ├── CandidateProfile.java        — JPA entity
│   ├── ExperienceEntry.java         — JPA entity; includes vector column
│   └── dto/
│       ├── ProfileRequest.java
│       ├── ProfileResponse.java
│       ├── ExperienceEntryRequest.java
│       └── ExperienceEntryResponse.java
│
├── tailoring/
│   ├── TailoringController.java
│   ├── TailoringService.java
│   ├── ResumeVersionRepository.java
│   ├── CoverLetterRepository.java
│   ├── ResumeVersion.java           — JPA entity
│   ├── CoverLetter.java             — JPA entity
│   └── dto/
│       ├── TailoringRequest.java
│       ├── TailoringResponse.java
│       ├── ResumeJsonPayload.java   — the LLM output schema
│       └── CoverLetterRequest.java
│
├── scoring/
│   ├── ScoringController.java
│   ├── ScoringService.java
│   ├── ScoringResultRepository.java
│   ├── ScoringResult.java           — JPA entity
│   └── dto/
│       ├── ScoringRequest.java
│       └── AtsGapReport.java
│
├── tracker/
│   ├── TrackerController.java
│   ├── TrackerService.java
│   ├── ApplicationRepository.java
│   ├── InterviewLogRepository.java
│   ├── Application.java             — JPA entity
│   ├── InterviewLog.java            — JPA entity (private)
│   └── dto/
│       ├── ApplicationRequest.java
│       ├── ApplicationResponse.java
│       ├── InterviewLogRequest.java
│       └── InterviewLogResponse.java
│
├── intel/
│   ├── IntelController.java
│   ├── IntelService.java
│   ├── CompanyIntelRepository.java
│   ├── CompanyInterviewIntel.java   — JPA entity (shared pool)
│   └── dto/
│       ├── IntelContributionRequest.java
│       └── CompanyIntelResponse.java
│
└── shared/
    ├── llm/
    │   ├── LlmGateway.java          — wraps Spring AI ChatClient; all LLM calls go here
    │   ├── LlmTaskConfig.java       — tiered model config (cheap vs strong)
    │   └── TokenUsageLogger.java
    ├── embedding/
    │   └── EmbeddingService.java    — wraps Spring AI EmbeddingClient
    ├── guardrails/
    │   ├── PiiRedactionService.java
    │   ├── PromptInjectionGuard.java
    │   ├── GroundingValidator.java
    │   └── JsonSchemaValidator.java
    ├── anonymization/
    │   └── AnonymizationService.java
    ├── rendering/
    │   └── LatexRenderingService.java
    └── metrics/
        └── MetricsCollector.java
```

### 2.3 Cross-Cutting Components

**LlmGateway** is the single point of entry for all LLM calls. It:
- Accepts a `LlmTask` enum (EXTRACTION, TAILORING, SCORING_AUGMENTATION, COVER_LETTER) that maps to a configured model tier.
- Applies `PromptInjectionGuard` to all user-supplied text before prompt assembly.
- Applies `PiiRedactionService` to prompt text before dispatch.
- Invokes Spring AI `ChatClient` with the configured model, token budget, and system prompt.
- Validates the response JSON against the task's registered schema via `JsonSchemaValidator`.
- Logs token usage via `TokenUsageLogger`.
- Applies `GroundingValidator` to tailoring responses.
- Returns a typed result object or throws a structured `LlmException`.

**LatexRenderingService** is the single point of entry for all PDF generation. It:
- Accepts a template ID and a `ResumeJsonPayload` (or `CoverLetterJsonPayload`).
- Loads the corresponding `.tex` template from the classpath template catalog.
- Performs token substitution (not LLM involvement — simple string replacement / Mustache-style templating).
- Invokes Tectonic as a subprocess with a configurable timeout.
- Returns the PDF bytes or throws a structured `RenderingException`.
- Never accepts LaTeX from the LLM or from user input.

**AnonymizationService** is the single point of entry for all writes to the shared pool. It:
- Accepts a private `InterviewLog` entity.
- Strips all personal identifier fields (defined exhaustively in the `anonymization` skill spec).
- Scrubs free-text fields using `PiiRedactionService`.
- Checks the minimum-aggregation threshold for the company before writing.
- Writes to `company_interview_intel` only if all checks pass.
- Is tested independently with dedicated unit and integration tests.

---

## 3. Data Flow

### 3.1 Authentication Flow

```
Client                    AuthController         AuthService          DB
  │                            │                     │                │
  │── POST /api/v1/auth/register ──────────────────► │                │
  │                            │  register(req)  ──► │                │
  │                            │                     │── INSERT user ►│
  │                            │                     │◄── user id ────│
  │                            │◄── JWT token ───────│                │
  │◄── 201 {token, userId} ────│                     │                │
  │                            │                     │                │
  │── POST /api/v1/auth/login ─────────────────────► │                │
  │                            │  login(req) ────── ►│                │
  │                            │                     │── SELECT user ►│
  │                            │                     │◄── user ───────│
  │                            │                     │  verify pw     │
  │                            │                     │  sign JWT      │
  │◄── 200 {token} ────────────│                     │                │
```

All subsequent requests carry `Authorization: Bearer <token>`. The Spring Security filter chain validates the token and populates `SecurityContextHolder` with a `UserPrincipal` containing the user ID. Every service method that accesses user data receives the user ID from the security context, never from the request body.

### 3.2 Job Ingestion Flow

```
Scheduler / Admin trigger
        │
        ▼
  JobService.ingestFromSource(sourceId)
        │
        ▼
  JobSource.fetchPostings()          ← AdzunaJobSource / RemoteOKJobSource
        │                              (rate-limited, ToS-compliant HTTP calls)
        ▼
  JobService.normalize(rawPosting)   ← extracts title, company, location,
        │                              skills, responsibilities, full text
        ▼
  JobRepository.save(jobPosting)     ← INSERT INTO job_postings
        │
        ▼
  (done — no LLM involved in ingestion)

Manual paste path:
  POST /api/v1/jobs/paste
        │
        ▼
  PromptInjectionGuard.sanitize(pastedText)   ← treat as untrusted
        │
        ▼
  ManualPasteJobSource.normalize(sanitizedText)
        │
        ▼
  JobRepository.save(jobPosting)
```

### 3.3 Experience Entry Embedding Flow

```
  POST /api/v1/profile/experience
        │
        ▼
  ProfileService.createExperienceEntry(userId, request)
        │
        ▼
  ExperienceRepository.save(entry)           ← INSERT (no vector yet)
        │
        ▼
  EmbeddingService.embed(entry.text)         ← Spring AI EmbeddingClient
        │                                       → configured embedding model
        ▼
  ExperienceRepository.updateVector(id, vec) ← UPDATE SET embedding = ?
        │
        ▼
  (done — vector stored in pgvector column)

Update path: same flow; re-embed on any text change (FR-17).
```

### 3.4 Resume Tailoring Pipeline (Core Value Flow)

```
  POST /api/v1/tailoring/tailor
  { jobPostingId, templateId }
        │
        ▼
  TailoringService.tailor(userId, jobPostingId, templateId)
        │
        ├─1─► JobRepository.findById(jobPostingId)
        │      → JobPosting { title, company, skills, fullText, ... }
        │
        ├─2─► EmbeddingService.embed(jobPosting.fullText)
        │      → queryVector
        │
        ├─3─► ExperienceRepository.findTopKByVector(userId, queryVector, k=10)
        │      → List<ExperienceEntry>  [pgvector cosine similarity, private to user]
        │
        ├─4─► PromptInjectionGuard.sanitize(jobPosting.fullText)
        │      → sanitizedJdText
        │
        ├─5─► LlmGateway.call(TAILORING, {
        │       systemPrompt: [grounding instructions + JSON schema],
        │       jdText: sanitizedJdText,
        │       experienceEntries: retrievedEntries,
        │       userProfile: profileSummary
        │     })
        │      │
        │      ├── PiiRedactionService.redact(promptText)
        │      ├── ChatClient.call(model=STRONG_MODEL, maxTokens=configured)
        │      ├── TokenUsageLogger.log(usage)
        │      ├── JsonSchemaValidator.validate(response, RESUME_SCHEMA)
        │      └── GroundingValidator.validate(response, retrievedEntries)
        │      → ResumeJsonPayload { summary, bullets[], skillsOrder[], ... }
        │
        ├─6─► LatexRenderingService.render(templateId, resumeJsonPayload)
        │      ├── load template from classpath catalog
        │      ├── inject JSON fields into template (Mustache substitution)
        │      ├── invoke Tectonic subprocess (timeout: 15s)
        │      └── return PDF bytes
        │      → byte[] pdfBytes
        │
        ├─7─► ResumeVersionRepository.save({
        │       userId, jobPostingId, templateId,
        │       jsonPayload, pdfBytes, createdAt
        │     })
        │      → ResumeVersion { id, ... }
        │
        └─8─► return TailoringResponse { resumeVersionId, downloadUrl, ... }

  GET /api/v1/tailoring/resume/{resumeVersionId}/pdf
        │
        ▼
  verify ownership (userId == resume.userId) → 403 if not
        │
        ▼
  return PDF bytes as application/pdf
```

### 3.5 ATS Scoring Flow

```
  POST /api/v1/scoring/score
  { resumeVersionId, jobPostingId }
        │
        ▼
  ScoringService.score(userId, resumeVersionId, jobPostingId)
        │
        ├─1─► verify ownership of resumeVersionId
        │
        ├─2─► JobRepository.findById(jobPostingId)
        │      → JobPosting
        │
        ├─3─► ResumeVersionRepository.findById(resumeVersionId)
        │      → ResumeVersion { jsonPayload }
        │
        ├─4─► KeywordExtractor.extract(jobPosting.fullText)
        │      → RequiredSkills { required[], preferred[], keywords[] }
        │      (deterministic NLP — no LLM; TF-IDF + regex patterns)
        │
        ├─5─► CoverageScorer.score(resumeJsonPayload, requiredSkills)
        │      → coverageScore (0.0–1.0), missingRequired[], missingPreferred[]
        │      (exact + normalized string matching)
        │
        ├─6─► EmbeddingService.embed(resumeJsonPayload.allText)
        │      → resumeVector
        │
        ├─7─► SemanticAugmenter.findNearMisses(
        │       resumeVector, requiredSkills, threshold=0.75)
        │      → nearMisses[] (skills semantically close but not exact matches)
        │
        ├─8─► FixGenerator.generate(missingRequired, nearMisses)
        │      → concreteFixesList[]
        │      (template-based, no LLM — maps missing skill → suggested bullet pattern)
        │
        ├─9─► ScoringResultRepository.save({
        │       userId, resumeVersionId, jobPostingId,
        │       coverageScore, missingRequired, nearMisses, fixes
        │     })
        │
        └─10─► return AtsGapReport { score, missing[], nearMisses[], fixes[] }
```

**Note on ATS scoring LLM usage:** Steps 4, 5, 7, and 8 are intentionally deterministic (no LLM). An optional LLM augmentation call (EXTRACTION tier model) may be added in Phase 4 to improve keyword extraction quality, but the scoring pipeline must function without it. This keeps costs low and the pipeline testable without mocks.

### 3.6 Interview Intel Contribution Flow (Privacy-Critical)

```
  POST /api/v1/tracker/applications/{appId}/interview-logs/{logId}/contribute
        │
        ▼
  TrackerService.contributeToPublicPool(userId, appId, logId)
        │
        ├─1─► verify ownership: appId.userId == userId, logId.appId == appId
        │
        ├─2─► InterviewLogRepository.findById(logId)
        │      → InterviewLog (PRIVATE — contains userId, appId, personal notes)
        │
        ├─3─► AnonymizationService.anonymize(interviewLog)
        │      ├── strip: userId, appId, userEmail, userName, applicationDate
        │      ├── strip: any field in PERSONAL_IDENTIFIER_FIELDS (defined in skill)
        │      ├── PiiRedactionService.redact(freeTextFields)
        │      ├── check: CompanyIntelRepository.countByCompany(company) >= MIN_THRESHOLD
        │      │    if below threshold: store in staging, do not publish yet
        │      └── return AnonymizedIntelRecord (no personal identifiers)
        │
        ├─4─► if threshold met:
        │      CompanyIntelRepository.save(anonymizedRecord)
        │      InterviewLog.markContributed()
        │
        └─5─► return ContributionResponse { status: PUBLISHED | STAGED }
```

### 3.7 Public Intel Browse Flow (No Auth Required)

```
  GET /api/v1/intel/companies/{companyName}
        │
        ▼
  IntelController (no @PreAuthorize — public endpoint)
        │
        ▼
  IntelService.getCompanyIntel(companyName)
        │
        ▼
  CompanyIntelRepository.findAggregatedByCompany(companyName)
  (queries ONLY company_interview_intel table — never touches private tables)
        │
        ▼
  return CompanyIntelResponse { company, rounds[], questions[], processNotes[], difficulty }
```

---

## 4. Service Boundaries

### 4.1 Internal Service Boundaries

The application is a monolith. "Service boundaries" within it are enforced by package visibility and dependency rules, not by network calls.

```
┌─────────────────────────────────────────────────────────────────────┐
│  BOUNDARY: API Surface                                              │
│  Controllers are the only classes that handle HTTP.                 │
│  Controllers call services; they never call repositories directly.  │
│  Controllers never contain business logic.                          │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  BOUNDARY: LLM Access                                               │
│  LlmGateway is the only class that calls Spring AI ChatClient.      │
│  No feature service calls ChatClient directly.                      │
│  All guardrails (PII, injection, grounding, schema) execute         │
│  inside LlmGateway before and after every LLM call.                │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  BOUNDARY: PDF Rendering                                            │
│  LatexRenderingService is the only class that invokes Tectonic.     │
│  No feature service constructs LaTeX strings.                       │
│  Templates are read-only classpath resources; no runtime writes     │
│  to the template catalog.                                           │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  BOUNDARY: Shared Pool Writes                                       │
│  AnonymizationService is the only class that writes to             │
│  company_interview_intel.                                           │
│  No feature service writes to this table directly.                  │
│  CompanyIntelRepository.save() is package-private to the           │
│  anonymization package; IntelService uses a read-only projection.   │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  BOUNDARY: User Identity                                            │
│  User ID is always sourced from SecurityContextHolder.              │
│  No endpoint accepts userId as a request parameter or body field.   │
│  Every repository method that returns user-scoped data requires     │
│  userId as a parameter and includes it in the WHERE clause.         │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 External Service Boundaries

| External Service | Direction | Protocol | Auth | Failure Mode |
|---|---|---|---|---|
| OpenAI-compatible LLM API (OpenRouter) | Outbound | HTTPS | Bearer API key (env var) | Timeout after 30s; return structured `LlmException`; never hang |
| Job Source APIs (Adzuna, RemoteOK) | Outbound | HTTPS | API key (env var) | Log error; skip batch; do not fail user request |
| Tectonic (LaTeX compiler) | Local subprocess | Process fork | None | Timeout after 15s; return `RenderingException` |
| Sentry | Outbound | HTTPS | DSN (env var) | Fire-and-forget; never block request path |
| PostgreSQL (managed) | Outbound | TCP/TLS | DB credentials (env var) | Connection pool; fail fast on startup if unreachable |

### 4.3 API Surface

All endpoints are under `/api/v1/`. The OpenAPI spec is auto-generated by Springdoc and served at `/api/v1/docs`.

```
Authentication (public):
  POST   /api/v1/auth/register
  POST   /api/v1/auth/login
  POST   /api/v1/auth/logout

Jobs (authenticated unless noted):
  GET    /api/v1/jobs                    — paginated listing (public read)
  GET    /api/v1/jobs/{id}               — single posting (public read)
  GET    /api/v1/jobs/search             — keyword/filter search (public read)
  POST   /api/v1/jobs/paste              — manual JD paste (authenticated)

Profile (authenticated, owner-only):
  GET    /api/v1/profile
  PUT    /api/v1/profile
  GET    /api/v1/profile/experience
  POST   /api/v1/profile/experience
  PUT    /api/v1/profile/experience/{id}
  DELETE /api/v1/profile/experience/{id}

Tailoring (authenticated, owner-only):
  POST   /api/v1/tailoring/tailor
  GET    /api/v1/tailoring/resumes
  GET    /api/v1/tailoring/resumes/{id}
  GET    /api/v1/tailoring/resumes/{id}/pdf
  POST   /api/v1/tailoring/cover-letter

Scoring (authenticated, owner-only):
  POST   /api/v1/scoring/score
  GET    /api/v1/scoring/results/{resumeVersionId}

Tracker (authenticated, owner-only):
  GET    /api/v1/tracker/applications
  POST   /api/v1/tracker/applications
  PUT    /api/v1/tracker/applications/{id}
  GET    /api/v1/tracker/applications/{id}/interview-logs
  POST   /api/v1/tracker/applications/{id}/interview-logs
  POST   /api/v1/tracker/applications/{id}/interview-logs/{logId}/contribute

Intel (public read, authenticated contribute):
  GET    /api/v1/intel/companies
  GET    /api/v1/intel/companies/{companyName}

Metrics (internal/admin):
  GET    /api/v1/metrics/ats-improvement
  GET    /api/v1/metrics/