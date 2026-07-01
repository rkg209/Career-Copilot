# Career Copilot — System Design

**Document version:** 1.0  
**Status:** Implementation-Ready  
**Builds on:** `architecture.md` v1.0

---

## Table of Contents

1. [Modules](#1-modules)
2. [Services](#2-services)
3. [Internal Workflows](#3-internal-workflows)
4. [Event Flows](#4-event-flows)
5. [State Transitions](#5-state-transitions)
6. [Design Patterns](#6-design-patterns)
7. [Integration Points](#7-integration-points)

---

## 1. Modules

The application is organized as a single deployable Spring Boot JAR. All source lives under `com.careercopilot`. The module boundary is the Java package; cross-package access rules are enforced by ArchUnit tests that run in CI.

### 1.1 Module Map

```
com.careercopilot
├── auth/           — identity, JWT issuance, credential management
├── jobs/           — job posting ingestion, normalization, search
├── profile/        — candidate profile, experience corpus, embeddings
├── tailoring/      — resume tailoring pipeline, PDF generation, cover letters
├── scoring/        — ATS gap analysis, keyword extraction, fix generation
├── tracker/        — application lifecycle, interview logs, contribution
├── intel/          — public company interview intelligence pool
└── shared/         — cross-cutting infrastructure (LLM, rendering, guardrails)
```

### 1.2 Module Dependency Rules

Dependencies flow strictly downstream. The table below is the authoritative allowed-dependency matrix. Any import that violates it fails the ArchUnit build gate.

```
Module        May import from
──────────    ─────────────────────────────────────────────────────
auth          shared
jobs          shared
profile       shared
tailoring     profile (read), jobs (read), shared
scoring       tailoring (read), jobs (read), profile (read), shared
tracker       jobs (read), tailoring (read), intel (write via shared), shared
intel         shared
shared        (nothing in com.careercopilot.*)
```

"Read" means the downstream module may call the upstream module's `*Service` or `*Repository` only through a defined read-only interface or DTO projection. It may never call an upstream repository's mutating methods.

### 1.3 Module Internal Structure

Every module follows an identical four-layer internal structure. The `tailoring` module is shown as the canonical example; all others mirror it.

```
tailoring/
├── TailoringController.java        — HTTP boundary; no business logic
├── TailoringService.java           — orchestration; owns the transaction boundary
├── ResumeVersionRepository.java    — Spring Data JPA; returns entities or projections
├── CoverLetterRepository.java
├── ResumeVersion.java              — JPA entity; maps to resume_versions table
├── CoverLetter.java                — JPA entity; maps to cover_letters table
└── dto/
    ├── TailoringRequest.java       — inbound; validated with Bean Validation
    ├── TailoringResponse.java      — outbound; never exposes JPA entities directly
    ├── ResumeJsonPayload.java      — the structured LLM output; used by rendering
    └── CoverLetterRequest.java
```

**Layer rules (enforced by ArchUnit):**

| Layer | May call | May not call |
|---|---|---|
| Controller | Service, DTO | Repository, LlmGateway, entity |
| Service | Repository, shared services, other module Services (read) | Controller, HTTP types |
| Repository | JPA / Spring Data | Service, Controller |
| DTO | (plain data) | anything |

### 1.4 Shared Module Internal Structure

The `shared` module is not a feature slice; it is a library of infrastructure adapters. It has no controller and no repository of its own.

```
shared/
├── llm/
│   ├── LlmGateway.java             — sole caller of Spring AI ChatClient
│   ├── LlmTask.java                — enum: EXTRACTION, TAILORING, SCORING_AUGMENTATION, COVER_LETTER
│   ├── LlmTaskConfig.java          — maps LlmTask → model name, token budget, temperature
│   ├── LlmRequest.java             — internal value object passed to LlmGateway
│   ├── LlmResponse.java            — typed wrapper around parsed JSON response
│   └── TokenUsageLogger.java       — writes token counts to metrics store
├── embedding/
│   └── EmbeddingService.java       — sole caller of Spring AI EmbeddingClient
├── guardrails/
│   ├── PiiRedactionService.java    — regex + NER-based PII scrubbing
│   ├── PromptInjectionGuard.java   — pattern-based injection detection and sanitization
│   ├── GroundingValidator.java     — verifies LLM output references only supplied experience
│   └── JsonSchemaValidator.java    — validates LLM JSON against registered JSON Schema
├── anonymization/
│   └── AnonymizationService.java   — sole writer to company_interview_intel
├── rendering/
│   └── LatexRenderingService.java  — sole invoker of Tectonic subprocess
└── metrics/
    └── MetricsCollector.java       — Micrometer-based counters and timers
```

---

## 2. Services

This section specifies every service class: its responsibilities, its constructor dependencies, and its public method signatures. Method signatures use Java syntax; return types are concrete where deterministic, `CompletableFuture` where the call is I/O-bound and benefits from non-blocking composition.

### 2.1 `AuthService`

**Responsibility:** User registration, login, and JWT lifecycle.

```java
@Service
@Transactional
public class AuthService {
    // Dependencies
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;       // BCryptPasswordEncoder
    private final JwtTokenProvider jwtTokenProvider;

    // Public API
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    void logout(String token);                           // adds token to denylist
}
```

**Key behaviors:**
- `register`: checks email uniqueness → hashes password with BCrypt (cost 12) → persists `User` entity → issues JWT.
- `login`: loads user by email → `passwordEncoder.matches()` → issues JWT on success; throws `AuthException` (mapped to 401) on failure. Constant-time comparison prevents timing attacks.
- `logout`: inserts token JTI into `jwt_denylist` table with TTL equal to token expiry. The Spring Security filter checks this table on every request.
- JWT secret and expiry are read from `JWT_SECRET` and `JWT_EXPIRY_SECONDS` environment variables via `JwtTokenProvider`.

### 2.2 `JwtTokenProvider`

**Responsibility:** Token signing, parsing, and validation. Not a `@Service`; annotated `@Component`.

```java
@Component
public class JwtTokenProvider {
    // Config injected from environment
    @Value("${JWT_SECRET}") private String secret;
    @Value("${JWT_EXPIRY_SECONDS}") private long expirySeconds;

    String generateToken(User user);
    Claims parseToken(String token);                     // throws JwtException subtypes
    boolean isTokenDenylisted(String jti);               // queries jwt_denylist
    UserPrincipal toPrincipal(Claims claims);
}
```

### 2.3 `JobService`

**Responsibility:** Job posting ingestion from external sources and manual paste; normalization; search.

```java
@Service
@Transactional
public class JobService {
    private final JobRepository jobRepository;
    private final List<JobSource> jobSources;            // injected by Spring (all implementations)
    private final PromptInjectionGuard injectionGuard;

    // Scheduled ingestion (called by @Scheduled or admin trigger)
    void ingestFromSource(String sourceId);

    // Manual paste (called by controller)
    JobPostingResponse createFromPaste(ManualJdPasteRequest request);

    // Query
    Page<JobPostingResponse> search(JobSearchRequest request, Pageable pageable);
    JobPostingResponse findById(Long jobPostingId);

    // Internal
    private JobPosting normalize(RawJobPosting raw);
}
```

**Key behaviors:**
- `ingestFromSource`: resolves the correct `JobSource` by `sourceId` → calls `fetchPostings()` → normalizes each → deduplicates by `(sourceId, externalId)` → bulk-saves. Errors per-posting are logged and skipped; the batch continues.
- `createFromPaste`: calls `injectionGuard.sanitize(pastedText)` before any processing. The sanitized text is passed to `ManualPasteJobSource.normalize()`.
- `normalize`: extracts structured fields (title, company, location, skills list, responsibilities, full text) from raw posting. No LLM involved.

### 2.4 `ProfileService`

**Responsibility:** Candidate profile CRUD and experience corpus management including embedding lifecycle.

```java
@Service
@Transactional
public class ProfileService {
    private final ProfileRepository profileRepository;
    private final ExperienceRepository experienceRepository;
    private final EmbeddingService embeddingService;

    ProfileResponse getProfile(Long userId);
    ProfileResponse updateProfile(Long userId, ProfileRequest request);

    ExperienceEntryResponse createExperience(Long userId, ExperienceEntryRequest request);
    ExperienceEntryResponse updateExperience(Long userId, Long entryId, ExperienceEntryRequest request);
    void deleteExperience(Long userId, Long entryId);
    List<ExperienceEntryResponse> listExperience(Long userId);
}
```

**Key behaviors:**
- `createExperience`: saves the `ExperienceEntry` entity first (gets a DB-assigned ID) → calls `embeddingService.embed(entry.buildEmbeddingText())` → updates the `embedding` vector column. The two-step write is intentional: the entity exists even if embedding fails, and a background retry can re-embed.
- `updateExperience`: re-embeds whenever any text field changes. Detects text change by comparing a SHA-256 hash of the text stored in `ExperienceEntry.textHash`.
- All repository calls include `userId` in the WHERE clause. `deleteExperience` verifies ownership before deletion; throws `ResourceNotFoundException` (mapped to 404) if not found, `AccessDeniedException` (mapped to 403) if owned by another user.

### 2.5 `TailoringService`

**Responsibility:** Orchestrates the core resume tailoring pipeline: retrieval, LLM call, rendering, persistence.

```java
@Service
@Transactional
public class TailoringService {
    private final JobRepository jobRepository;
    private final ExperienceRepository experienceRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final EmbeddingService embeddingService;
    private final LlmGateway llmGateway;
    private final LatexRenderingService latexRenderingService;
    private final PromptInjectionGuard injectionGuard;

    TailoringResponse tailor(Long userId, TailoringRequest request);
    CoverLetterResponse generateCoverLetter(Long userId, CoverLetterRequest request);
    Page<TailoringResponse> listResumes(Long userId, Pageable pageable);
    TailoringResponse getResume(Long userId, Long resumeVersionId);
    byte[] getResumePdf(Long userId, Long resumeVersionId);
}
```

**Key behaviors:** See §3.1 (Resume Tailoring Workflow) for the full step-by-step.

- `tailor` is the most complex method in the system. It is `@Transactional` with `REQUIRES_NEW` propagation so that a rendering failure after a successful LLM call does not roll back the saved `ResumeJsonPayload` — the JSON is always persisted even if PDF generation fails, allowing retry.
- `getResumePdf`: verifies `resumeVersion.userId == userId` before returning bytes. Returns `byte[]` with `Content-Type: application/pdf` and `Content-Disposition: attachment`.

### 2.6 `ScoringService`

**Responsibility:** ATS gap analysis pipeline — keyword extraction, coverage scoring, semantic near-miss detection, fix generation.

```java
@Service
@Transactional(readOnly = true)
public class ScoringService {
    private final JobRepository jobRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ScoringResultRepository scoringResultRepository;
    private final EmbeddingService embeddingService;
    private final KeywordExtractor keywordExtractor;
    private final CoverageScorer coverageScorer;
    private final SemanticAugmenter semanticAugmenter;
    private final FixGenerator fixGenerator;

    AtsGapReport score(Long userId, ScoringRequest request);
    AtsGapReport getResult(Long userId, Long resumeVersionId);
}
```

**Key behaviors:** See §3.2 (ATS Scoring Workflow). The `@Transactional(readOnly = true)` annotation covers the read phase; the save at step 9 uses a nested `@Transactional(propagation = REQUIRES_NEW)` on `ScoringResultRepository.save()`.

**Sub-components of `ScoringService` (package-private, injected by Spring):**

```java
// All in scoring/ package; not exposed outside it
class KeywordExtractor {
    RequiredSkills extract(String jobFullText);
    // TF-IDF over job text + regex patterns for common skill tokens
    // No LLM. Returns required[], preferred[], keywords[]
}

class CoverageScorer {
    CoverageResult score(ResumeJsonPayload resume, RequiredSkills skills);
    // Exact match + normalized match (lowercase, stemmed)
    // Returns coverageScore (0.0–1.0), missingRequired[], missingPreferred[]
}

class SemanticAugmenter {
    List<NearMiss> findNearMisses(float[] resumeVector,
                                  RequiredSkills skills,
                                  double threshold);
    // Embeds each required skill term; cosine similarity against resumeVector
    // Returns skills semantically close but not exact matches
}

class FixGenerator {
    List<ConcreteFix> generate(List<String> missingRequired,
                               List<NearMiss> nearMisses);
    // Template-based: maps missing skill → suggested bullet pattern
    // No LLM. Patterns loaded from classpath fix-templates.yaml
}
```

### 2.7 `TrackerService`

**Responsibility:** Application lifecycle management, interview log CRUD, and contribution to the public intel pool.

```java
@Service
@Transactional
public class TrackerService {
    private final ApplicationRepository applicationRepository;
    private final InterviewLogRepository interviewLogRepository;
    private final AnonymizationService anonymizationService;

    Page<ApplicationResponse> listApplications(Long userId, Pageable pageable);
    ApplicationResponse createApplication(Long userId, ApplicationRequest request);
    ApplicationResponse updateApplication(Long userId, Long appId, ApplicationRequest request);

    List<InterviewLogResponse> listInterviewLogs(Long userId, Long appId);
    InterviewLogResponse createInterviewLog(Long userId, Long appId, InterviewLogRequest request);

    ContributionResponse contributeToPublicPool(Long userId, Long appId, Long logId);
}
```

**Key behaviors:**
- `updateApplication`: validates that `application.userId == userId`. Drives the `ApplicationStatus` state machine (see §5.1).
- `contributeToPublicPool`: delegates entirely to `AnonymizationService.anonymize()`. `TrackerService` never touches `company_interview_intel` directly.

### 2.8 `IntelService`

**Responsibility:** Read-only access to the public company interview intelligence pool.

```java
@Service
@Transactional(readOnly = true)
public class IntelService {
    private final CompanyIntelRepository companyIntelRepository;

    Page<CompanyIntelResponse> listCompanies(Pageable pageable);
    CompanyIntelResponse getCompanyIntel(String companyName);
}
```

**Key behaviors:**
- Queries only `company_interview_intel`. Never touches `interview_logs`, `applications`, or any user-scoped table.
- `CompanyIntelRepository` exposes only read methods to `IntelService`. The `save()` method is package-private to `shared.anonymization`.

### 2.9 `LlmGateway` (shared)

**Responsibility:** Single point of entry for all LLM calls. Applies all guardrails. Returns typed results.

```java
@Service
public class LlmGateway {
    private final ChatClient chatClient;                 // Spring AI
    private final LlmTaskConfig taskConfig;
    private final PiiRedactionService piiRedaction;
    private final PromptInjectionGuard injectionGuard;
    private final JsonSchemaValidator schemaValidator;
    private final GroundingValidator groundingValidator;
    private final TokenUsageLogger tokenUsageLogger;
    private final MetricsCollector metrics;

    <T> LlmResponse<T> call(LlmTask task, LlmRequest request, Class<T> responseType);
}
```

**Internal execution sequence (every call):**

```
1. taskConfig.resolve(task)          → ModelSpec { modelName, maxTokens, temperature }
2. injectionGuard.check(request)     → throws PromptInjectionException if detected
3. piiRedaction.redact(request)      → sanitized LlmRequest (mutates a copy)
4. buildPrompt(modelSpec, sanitized) → ChatPrompt { systemMessage, userMessage }
5. chatClient.call(prompt)           → raw String response
6. tokenUsageLogger.log(task, usage) → async write to token_usage_log table
7. schemaValidator.validate(raw, task.schema()) → throws SchemaValidationException if invalid
8. parseJson(raw, responseType)      → T typedResult
9. if task.requiresGrounding():
     groundingValidator.validate(typedResult, request.groundingContext())
     → throws GroundingException if hallucination detected
10. metrics.recordLlmCall(task, latencyMs, tokenCount)
11. return LlmResponse<T> { result: typedResult, usage: tokenUsage }
```

**Retry policy:** Steps 5–9 are wrapped in a `Resilience4j` retry with `maxAttempts=2`, `waitDuration=1s`, retrying only on `SchemaValidationException` (malformed JSON from LLM). `LlmException` (timeout, API error) is not retried — it propagates immediately.

### 2.10 `EmbeddingService` (shared)

```java
@Service
public class EmbeddingService {
    private final EmbeddingClient embeddingClient;       // Spring AI
    private final MetricsCollector metrics;

    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
}
```

**Key behaviors:**
- `embed`: truncates input to `MAX_EMBEDDING_TOKENS` (configured via env var) before dispatch. Logs a warning if truncation occurs.
- `embedBatch`: used during bulk ingestion. Respects the embedding API's batch size limit (configured via `EMBEDDING_BATCH_SIZE` env var).
- Results are never cached in memory — the caller (e.g., `ProfileService`) is responsible for persisting vectors.

### 2.11 `AnonymizationService` (shared)

```java
@Service
@Transactional
public class AnonymizationService {
    private final CompanyIntelRepository companyIntelRepository;
    private final PiiRedactionService piiRedaction;
    private final AnonymizationConfig config;            // MIN_THRESHOLD from env

    ContributionResult anonymize(InterviewLog log);
}
```

**Internal execution sequence:**

```
1. Strip structural PII fields:
   userId, appId, userEmail, userName, applicationDate,
   and all fields in PERSONAL_IDENTIFIER_FIELDS (config-driven list)
2. piiRedaction.redact(log.notes, log.questions, log.feedback)
   → scrubbed free-text fields
3. Build AnonymizedIntelRecord { company, role, round, questions, difficulty, ... }
4. count = companyIntelRepository.countByCompany(company)
   if count < MIN_THRESHOLD:
     companyIntelRepository.saveToStaging(anonymizedRecord)
     return ContributionResult.STAGED
5. companyIntelRepository.save(anonymizedRecord)
   interviewLog.markContributed()                        — sets contributed_at timestamp
6. return ContributionResult.PUBLISHED
```

### 2.12 `LatexRenderingService` (shared)

```java
@Service
public class LatexRenderingService {
    private final TemplateRegistry templateRegistry;     // loads from classpath
    private final RenderingConfig config;                // TECTONIC_PATH, TIMEOUT_SECONDS

    byte[] render(String templateId, ResumeJsonPayload payload);
    byte[] render(String templateId, CoverLetterJsonPayload payload);
}
```

**Internal execution sequence:**

```
1. templateRegistry.load(templateId)
   → InputStream of .tex template from classpath:/templates/{templateId}.tex
   → throws TemplateNotFoundException if not found
2. MustacheRenderer.render(templateContent, payload.toMap())
   → populated LaTeX string
   → throws RenderingException if any required field is missing
3. Write populated LaTeX to temp file in isolated temp directory
4. ProcessBuilder.command(TECTONIC_PATH, "--outdir", tempDir, texFilePath)
   .start()
   → Process handle
5. process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
   → if timeout: process.destroyForcibly(); throw RenderingException("timeout")
6. if process.exitValue() != 0:
   → read stderr; throw RenderingException(stderr)
7. Read PDF bytes from tempDir output file
8. Delete temp directory (finally block)
9. return pdfBytes
```

**Security invariants:**
- Templates are classpath resources. No template is ever written at runtime.
- The LLM never produces LaTeX. It produces JSON; the service performs substitution.
- The temp directory is created with `Files.createTempDirectory()` using a random suffix; no predictable path.
- Tectonic runs without network access (no `--allow-network` flag).

---

## 3. Internal Workflows

### 3.1 Resume Tailoring Workflow

This is the system's primary value-delivery path. Every step is numbered to match the architecture document's flow diagram.

```
TailoringController.tailor(request)
  │  @PreAuthorize("isAuthenticated()")
  │  userId = SecurityContextHolder → UserPrincipal.userId
  │
  ▼
TailoringService.tailor(userId, request)
  │
  │ Step 1: Load job posting
  ├─► JobRepository.findById(request.jobPostingId)
  │     → JobPosting { id, title, company, skills[], fullText, ... }
  │     → throws ResourceNotFoundException if absent
  │
  │ Step 2: Embed job description
  ├─► EmbeddingService.embed(jobPosting.fullText)
  │     → float[] queryVector
  │
  │ Step 3: Retrieve relevant experience (RAG retrieval)
  ├─► ExperienceRepository.findTopKByVector(userId, queryVector, k=10)
  │     SQL: SELECT * FROM experience_entries
  │          WHERE user_id = :userId
  │          ORDER BY embedding <=> :queryVector   -- pgvector cosine operator
  │          LIMIT 10
  │     → List<ExperienceEntry> (private to this user)
  │
  │ Step 4: Sanitize job description text
  ├─► PromptInjectionGuard.sanitize(jobPosting.fullText)
  │     → String sanitizedJdText
  │
  │ Step 5: LLM call (TAILORING task)
  ├─► LlmGateway.call(
  │     task = LlmTask.TAILORING,
  │     request = LlmRequest {
  │       systemPrompt: TAILORING_SYSTEM_PROMPT,   -- grounding + JSON schema instructions
  │       context: {
  │         jdText: sanitizedJdText,
  │         experienceEntries: retrievedEntries,
  │         profileSummary: profileService.getSummary(userId)
  │       },
  │       groundingContext: retrievedEntries        -- for GroundingValidator
  │     },
  │     responseType = ResumeJsonPayload.class
  │   )
  │     → LlmResponse<ResumeJsonPayload>
  │     → throws LlmException on API failure
  │     → throws SchemaValidationException on malformed response (retried once)
  │     → throws GroundingException if hallucinated content detected
  │
  │ Step 6: Render PDF
  ├─► LatexRenderingService.render(request.templateId, resumeJsonPayload)
  │     → byte[] pdfBytes
  │     → throws RenderingException on Tectonic failure
  │
  │ Step 7: Persist (REQUIRES_NEW transaction)
  ├─► ResumeVersionRepository.save(ResumeVersion {
  │     userId, jobPostingId, templateId,
  │     jsonPayload: resumeJsonPayload,
  │     pdfBytes: pdfBytes,
  │     llmModel: llmResponse.usage.model,
  │     tokenCount: llmResponse.usage.totalTokens,
  │     createdAt: Instant.now()
  │   })
  │     → ResumeVersion { id, ... }
  │
  │ Step 8: Build and return response
  └─► return TailoringResponse {
        resumeVersionId: savedVersion.id,
        downloadUrl: "/api/v1/tailoring/resumes/{id}/pdf",
        previewUrl: "/api/v1/tailoring/resumes/{id}",
        createdAt: savedVersion.createdAt
      }
```

**Transaction boundary:** Steps 1–6 run in the outer `@Transactional` context. Step 7 uses `REQUIRES_NEW` so that the JSON payload is committed even if the outer transaction rolls back (e.g., due to a downstream error). This ensures the expensive LLM call result is never lost.

**Error handling matrix:**

| Failure point | Exception | HTTP response | Recovery |
|---|---|---|---|
| Job not found | `ResourceNotFoundException` | 404 | Client corrects request |
| Embedding API down | `EmbeddingException` | 503 | Retry with backoff |
| Injection detected | `PromptInjectionException` | 400 | Client cleans input |
| LLM timeout | `LlmException` | 503 | Retry with backoff |
| Schema invalid (after retry) | `SchemaValidationException` | 502 | Alert; investigate prompt |
| Grounding failure | `GroundingException` | 422 | Log; return partial result |
| Tectonic timeout | `RenderingException` | 503 | JSON saved; PDF retry available |
| Tectonic error | `RenderingException` | 500 | Log stderr; alert |

### 3.2 ATS Scoring Workflow

```
ScoringController.score(request)
  │  userId from SecurityContextHolder
  │
  ▼
ScoringService.score(userId, request)
  │
  │ Step 1: Ownership verification
  ├─► ResumeVersionRepository.findByIdAndUserId(request.resumeVersionId, userId)
  │     → throws AccessDeniedException if userId mismatch
  │
  │ Step 2: Load job posting
  ├─► JobRepository.findById(request.jobPostingId)
  │     → JobPosting
  │
  │ Step 3: Load resume JSON
  ├─► (already loaded in step 1) ResumeVersion.jsonPayload → ResumeJsonPayload
  │
  │ Step 4: Keyword extraction (deterministic, no LLM)
  ├─► KeywordExtractor.extract(jobPosting.fullText)
  │     TF-IDF scoring over job text
  │     + regex patterns for skill tokens (e.g., "Java", "AWS", "5+ years")
  │     → RequiredSkills { required[], preferred[], keywords[] }
  │
  │ Step 5: Coverage scoring (deterministic)
  ├─► CoverageScorer.score(resumeJsonPayload, requiredSkills)
  │     Exact match + normalized match (lowercase, Porter stemmer)
  │     → CoverageResult {
  │         coverageScore: 0.0–1.0,
  │         missingRequired: [],
  │         missingPreferred: []
  │       }
  │
  │ Step 6: Embed resume text
  ├─► EmbeddingService.embed(resumeJsonPayload.allText())
  │     → float[] resumeVector
  │
  │ Step 7: Semantic near-miss detection
  ├─► SemanticAugmenter.findNearMisses(resumeVector, requiredSkills, threshold=0.75)
  │     For each required skill not in exact matches:
  │       embed(skillTerm) → skillVector
  │       cosineSimilarity(resumeVector, skillVector)
  │       if similarity >= 0.75: add to nearMisses
  │     → List<NearMiss> { skill, similarity, suggestedPhrase }
  │
  │ Step 8: Fix generation (template-based, no LLM)
  ├─► FixGenerator.generate(coverageResult.missingRequired, nearMisses)
  │     Loads fix-templates.yaml from classpath
  │     Maps each missing skill to a bullet pattern template
  │     → List<ConcreteFix> { skill, suggestedBullet, priority }
  │
  │ Step 9: Persist result
  ├─► ScoringResultRepository.save(ScoringResult {
  │     userId, resumeVersionId, jobPostingId,
  │     coverageScore, missingRequired, nearMisses, fixes,
  │     scoredAt: Instant.now()
  │   })
  │
  └─► return AtsGapReport {
        score: coverageResult.coverageScore,
        missing: coverageResult.missingRequired,
        preferred: coverageResult.missingPreferred,
        nearMisses: nearMisses,
        fixes: fixes,
        scoredAt: result.scoredAt
      }
```

### 3.3 Interview Intel Contribution Workflow

```
TrackerController.contribute(appId, logId)
  │  userId from SecurityContextHolder
  │
  ▼
TrackerService.contributeToPublicPool(userId, appId, logId)
  │
  │ Step 1: Ownership chain verification
  ├─► ApplicationRepository.findByIdAndUserId(appId, userId)
  │     → Application or AccessDeniedException
  ├─► InterviewLogRepository.findByIdAndApplicationId(logId, appId)
  │     → InterviewLog or ResourceNotFoundException
  │
  │ Step 2: Guard: already contributed?
  ├─► if interviewLog.contributedAt != null:
  │     return ContributionResponse { status: ALREADY_CONTRIBUTED }
  │
  │ Step 3: Delegate to AnonymizationService
  ├─