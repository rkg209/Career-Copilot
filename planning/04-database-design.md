# Career Copilot — Database Design

**Document version:** 1.0
**Status:** Implementation-Ready
**Builds on:** `architecture.md` v1.0, `system-design.md` v1.0

---

## Table of Contents

1. [Design Principles](#1-design-principles)
2. [Schema Overview](#2-schema-overview)
3. [Entity Specifications](#3-entity-specifications)
4. [Relationships and Foreign Keys](#4-relationships-and-foreign-keys)
5. [Multi-Tenancy Model](#5-multi-tenancy-model)
6. [Data Ownership by Module](#6-data-ownership-by-module)
7. [The Private/Public Data Split](#7-the-privatepublic-data-split)
8. [Vector Storage](#8-vector-storage)
9. [Indexes](#9-indexes)
10. [Constraints and Invariants](#10-constraints-and-invariants)
11. [Migration Strategy](#11-migration-strategy)
12. [Schema Diagram](#12-schema-diagram)

---

## 1. Design Principles

### 1.1 Normalization Target

The schema targets **Third Normal Form (3NF)** throughout, with two deliberate exceptions:

- `resume_versions.pdf_bytes` — a large binary column stored in-row rather than in a separate blob store, justified by the single-database constraint and the expectation that PDF retrieval always accompanies the version metadata.
- `scoring_results.missing_required`, `scoring_results.near_misses`, `scoring_results.fixes` — stored as `jsonb` arrays rather than child tables, because these are computed read-only artifacts that are always consumed as a unit and never queried element-by-element at the database level.

All other repeating groups, multi-valued attributes, and transitive dependencies are normalized into separate tables.

### 1.2 Naming Conventions

| Object | Convention | Example |
|---|---|---|
| Tables | `snake_case`, plural | `experience_entries` |
| Columns | `snake_case` | `created_at` |
| Primary keys | `id` (bigserial) on every table | `id BIGINT GENERATED ALWAYS AS IDENTITY` |
| Foreign keys | `{referenced_table_singular}_id` | `user_id`, `application_id` |
| Timestamps | `created_at`, `updated_at` (both `TIMESTAMPTZ`) | — |
| Soft-delete flag | `deleted_at TIMESTAMPTZ NULL` | — |
| Boolean flags | `is_{condition}` or past-tense verb | `is_active`, `contributed_at` |
| Indexes | `idx_{table}_{columns}` | `idx_experience_entries_user_id` |
| Unique constraints | `uq_{table}_{columns}` | `uq_users_email` |
| Check constraints | `ck_{table}_{condition}` | `ck_applications_status` |

### 1.3 Soft Deletes

Tables that represent user-owned content use `deleted_at TIMESTAMPTZ NULL` rather than hard deletes. A `NULL` value means the row is live; a non-null value means it is logically deleted. All application-layer queries append `WHERE deleted_at IS NULL`. Hard deletes are reserved for the `jwt_denylist` (TTL-based purge) and the anonymization staging table (after promotion).

Tables that are append-only audit or log records (`token_usage_log`, `scoring_results`) are never deleted.

### 1.4 Timestamps

Every table carries `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`. Tables representing mutable state also carry `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`, maintained by a shared trigger function `set_updated_at()` applied at migration time.

### 1.5 Identity Columns

All primary keys use `BIGINT GENERATED ALWAYS AS IDENTITY` (SQL standard, PostgreSQL 10+). This is preferred over `SERIAL` because it prevents accidental override via `INSERT ... (id, ...)`. Application code never generates or supplies primary key values.

---

## 2. Schema Overview

The database contains **17 tables** organized into three logical zones:

```
┌─────────────────────────────────────────────────────────────────────────┐
│  ZONE 1 — IDENTITY & INFRASTRUCTURE                                     │
│  (not owned by any feature module; owned by auth and shared)            │
│                                                                         │
│  users                 — registered accounts                            │
│  jwt_denylist          — revoked token JTIs                             │
│  token_usage_log       — LLM token consumption audit                    │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│  ZONE 2 — PRIVATE USER DATA                                             │
│  (every row is owned by exactly one user; user_id is always present     │
│   and always in the WHERE clause of every query)                        │
│                                                                         │
│  candidate_profiles    — profile module                                 │
│  experience_entries    — profile module (includes pgvector column)      │
│  job_postings          — jobs module (shared read, user-created rows    │
│                          for manual pastes carry user_id)               │
│  resume_versions       — tailoring module                               │
│  cover_letters         — tailoring module                               │
│  scoring_results       — scoring module                                 │
│  applications          — tracker module                                 │
│  interview_logs        — tracker module (most sensitive private data)   │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│  ZONE 3 — SHARED PUBLIC DATA                                            │
│  (no user_id; written only through AnonymizationService;                │
│   readable without authentication)                                      │
│                                                                         │
│  company_interview_intel         — intel module (published pool)        │
│  company_intel_staging           — intel module (below-threshold buffer)│
└─────────────────────────────────────────────────────────────────────────┘
```

`job_postings` straddles zones: rows ingested from external sources carry `user_id = NULL` and are shared read; rows created via manual paste carry the submitting user's `user_id`. The access pattern treats them identically for reads but restricts mutation to the owner.

---

## 3. Entity Specifications

Each table is specified with its DDL, column-level rationale, and the JPA entity that maps to it.

---

### 3.1 `users`

**Module owner:** `auth`
**Zone:** Identity

```sql
CREATE TABLE users (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           TEXT            NOT NULL,
    password_hash   TEXT            NOT NULL,
    display_name    TEXT            NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ     NULL,

    CONSTRAINT uq_users_email UNIQUE (email)
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `email` | Unique login identifier. Stored lowercase (enforced by application layer before insert). |
| `password_hash` | BCrypt output (cost 12). Never the raw password. |
| `display_name` | Display only; not used for auth. |
| `is_active` | Allows account suspension without deletion. |
| `deleted_at` | Soft delete. A deleted user's rows in other tables remain for audit; cascade is handled by application logic, not DB cascade. |

**JPA entity:** `auth.User`

---

### 3.2 `jwt_denylist`

**Module owner:** `auth`
**Zone:** Identity

```sql
CREATE TABLE jwt_denylist (
    jti         TEXT            NOT NULL PRIMARY KEY,
    user_id     BIGINT          NOT NULL REFERENCES users(id),
    expires_at  TIMESTAMPTZ     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `jti` | JWT ID claim. The primary key is the JTI string itself; no surrogate key needed. |
| `expires_at` | Copied from the token's `exp` claim. A scheduled job purges rows where `expires_at < now()` to keep the table small. |

**Access pattern:** The Spring Security filter calls `SELECT 1 FROM jwt_denylist WHERE jti = ? AND expires_at > now()` on every authenticated request. The primary key index makes this O(1).

**No soft delete:** Rows are hard-deleted by the TTL purge job. There is no audit requirement for expired denylisted tokens.

---

### 3.3 `token_usage_log`

**Module owner:** `shared`
**Zone:** Identity / Infrastructure

```sql
CREATE TABLE token_usage_log (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT          NULL REFERENCES users(id),
    llm_task        TEXT            NOT NULL,
    model_name      TEXT            NOT NULL,
    prompt_tokens   INTEGER         NOT NULL,
    completion_tokens INTEGER        NOT NULL,
    total_tokens    INTEGER         NOT NULL,
    latency_ms      INTEGER         NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_token_usage_log_tokens
        CHECK (prompt_tokens >= 0
           AND completion_tokens >= 0
           AND total_tokens = prompt_tokens + completion_tokens)
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `user_id` | Nullable: system-level calls (e.g., batch ingestion) have no user context. |
| `llm_task` | Matches `LlmTask` enum values: `EXTRACTION`, `TAILORING`, `SCORING_AUGMENTATION`, `COVER_LETTER`. |
| `model_name` | Recorded at call time; model config may change over time. |

**Append-only:** No updates, no deletes. Used for cost attribution and the `/api/v1/metrics/` endpoints.

---

### 3.4 `candidate_profiles`

**Module owner:** `profile`
**Zone:** Private

```sql
CREATE TABLE candidate_profiles (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    headline        TEXT            NULL,
    summary         TEXT            NULL,
    location        TEXT            NULL,
    linkedin_url    TEXT            NULL,
    github_url      TEXT            NULL,
    website_url     TEXT            NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_candidate_profiles_user_id UNIQUE (user_id)
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `user_id` | One profile per user; the unique constraint enforces this at the DB level. |
| `headline`, `summary` | Free text; used as context in the tailoring prompt. |
| `linkedin_url` etc. | Optional enrichment fields; validated as URLs by the application layer. |

**No `deleted_at`:** Profile deletion is handled by deleting the `users` row (soft delete), which logically orphans the profile. A separate GDPR erasure job handles hard deletion of all user data on request.

**JPA entity:** `profile.CandidateProfile`

---

### 3.5 `experience_entries`

**Module owner:** `profile`
**Zone:** Private

```sql
CREATE EXTENSION IF NOT EXISTS vector;   -- pgvector; applied in initial migration

CREATE TABLE experience_entries (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    company         TEXT            NOT NULL,
    title           TEXT            NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE            NULL,           -- NULL means current role
    is_current      BOOLEAN         NOT NULL DEFAULT FALSE,
    description     TEXT            NOT NULL,       -- free-text; the embedding source
    achievements    TEXT[]          NOT NULL DEFAULT '{}',
    skills_used     TEXT[]          NOT NULL DEFAULT '{}',
    text_hash       TEXT            NOT NULL,       -- SHA-256 of embeddable text fields
    embedding       VECTOR(1536)    NULL,           -- NULL until EmbeddingService runs
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ     NULL,

    CONSTRAINT ck_experience_entries_dates
        CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_experience_entries_current
        CHECK (NOT (is_current = TRUE AND end_date IS NOT NULL))
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `description` | The primary source text for embedding. Combined with `achievements` and `skills_used` to form the embedding input via `buildEmbeddingText()`. |
| `achievements` | PostgreSQL native array. Stored as `TEXT[]` rather than a child table because achievements are always read and written as a unit with the entry. |
| `skills_used` | Same rationale as `achievements`. |
| `text_hash` | SHA-256 of the concatenated embeddable text fields. `ProfileService.updateExperience` compares the incoming hash to the stored hash to decide whether re-embedding is needed, avoiding unnecessary embedding API calls. |
| `embedding` | `VECTOR(1536)` — dimension matches `text-embedding-3-small` (OpenAI default). Dimension is configurable via `EMBEDDING_DIMENSIONS` env var; a migration is required to change it. `NULL` is valid immediately after insert; the embedding is written in a second UPDATE. |
| `deleted_at` | Soft delete. Deleted entries are excluded from RAG retrieval queries. |

**JPA entity:** `profile.ExperienceEntry`

**Embedding text construction** (application layer, not DB):
```
buildEmbeddingText() =
    title + " at " + company + ". " +
    description + " " +
    String.join(" ", achievements) + " " +
    String.join(" ", skills_used)
```

---

### 3.6 `job_postings`

**Module owner:** `jobs`
**Zone:** Shared read; private for user-created rows

```sql
CREATE TABLE job_postings (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT          NULL REFERENCES users(id),  -- NULL = ingested; non-null = manual paste
    source_id       TEXT            NULL,           -- 'adzuna', 'remoteok', 'usajobs', NULL for paste
    external_id     TEXT            NULL,           -- source's own ID; used for deduplication
    title           TEXT            NOT NULL,
    company         TEXT            NOT NULL,
    location        TEXT            NULL,
    remote_type     TEXT            NULL,           -- 'remote', 'hybrid', 'onsite', NULL=unknown
    employment_type TEXT            NULL,           -- 'full_time', 'contract', etc.
    salary_min      INTEGER         NULL,
    salary_max      INTEGER         NULL,
    salary_currency TEXT            NULL DEFAULT 'USD',
    skills          TEXT[]          NOT NULL DEFAULT '{}',
    responsibilities TEXT[]         NOT NULL DEFAULT '{}',
    full_text       TEXT            NOT NULL,       -- complete JD text; used for embedding and scoring
    url             TEXT            NULL,
    posted_at       TIMESTAMPTZ     NULL,
    expires_at      TIMESTAMPTZ     NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_job_postings_source_external
        UNIQUE NULLS NOT DISTINCT (source_id, external_id),

    CONSTRAINT ck_job_postings_salary
        CHECK (salary_max IS NULL OR salary_min IS NULL
               OR salary_max >= salary_min),

    CONSTRAINT ck_job_postings_source_consistency
        CHECK (
            (source_id IS NULL AND external_id IS NULL)  -- manual paste
         OR (source_id IS NOT NULL AND external_id IS NOT NULL)  -- ingested
        )
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `user_id` | `NULL` for externally ingested postings (shared). Non-null for manual pastes (private to that user for mutation; readable by all for the tailoring pipeline). |
| `source_id` + `external_id` | Composite deduplication key. `UNIQUE NULLS NOT DISTINCT` (PostgreSQL 15+) allows multiple `NULL` pairs (manual pastes) while enforcing uniqueness for ingested rows. |
| `full_text` | The complete, unsanitized job description text. Sanitization happens at the application layer before this text enters any prompt. Stored raw to allow re-processing if sanitization logic changes. |
| `skills`, `responsibilities` | Extracted during normalization. `TEXT[]` for the same reasons as `experience_entries`. |

**JPA entity:** `jobs.JobPosting`

---

### 3.7 `resume_versions`

**Module owner:** `tailoring`
**Zone:** Private

```sql
CREATE TABLE resume_versions (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id),
    job_posting_id      BIGINT          NOT NULL REFERENCES job_postings(id),
    template_id         TEXT            NOT NULL,
    json_payload        JSONB           NOT NULL,   -- ResumeJsonPayload; the LLM output
    pdf_bytes           BYTEA           NULL,       -- NULL if rendering failed; retryable
    llm_model           TEXT            NOT NULL,
    token_count         INTEGER         NOT NULL,
    render_status       TEXT            NOT NULL DEFAULT 'PENDING',
    render_error        TEXT            NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ     NULL,

    CONSTRAINT ck_resume_versions_render_status
        CHECK (render_status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_resume_versions_pdf_consistency
        CHECK (
            (render_status = 'SUCCESS' AND pdf_bytes IS NOT NULL)
         OR (render_status IN ('PENDING', 'FAILED') AND pdf_bytes IS NULL)
        )
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `json_payload` | `JSONB` stores the full `ResumeJsonPayload` structure. Stored as JSONB (not TEXT) to allow future JSON path queries (e.g., "find all resumes that mention skill X"). |
| `pdf_bytes` | `BYTEA` in-row. Acceptable because PDFs are typically 50–200 KB and are always fetched alongside the version metadata. A future migration could move this to object storage if row sizes become a concern. |
| `render_status` | Tracks the PDF rendering lifecycle independently of the JSON payload. The JSON is always committed first (in a `REQUIRES_NEW` transaction); rendering is a subsequent step that can fail and be retried. |
| `llm_model` | Recorded at generation time for cost attribution and reproducibility auditing. |
| `token_count` | Denormalized from `token_usage_log` for fast per-resume cost display. |

**JPA entity:** `tailoring.ResumeVersion`

---

### 3.8 `cover_letters`

**Module owner:** `tailoring`
**Zone:** Private

```sql
CREATE TABLE cover_letters (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id),
    job_posting_id      BIGINT          NOT NULL REFERENCES job_postings(id),
    resume_version_id   BIGINT          NULL REFERENCES resume_versions(id),
    json_payload        JSONB           NOT NULL,
    pdf_bytes           BYTEA           NULL,
    render_status       TEXT            NOT NULL DEFAULT 'PENDING',
    render_error        TEXT            NULL,
    llm_model           TEXT            NOT NULL,
    token_count         INTEGER         NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ     NULL,

    CONSTRAINT ck_cover_letters_render_status
        CHECK (render_status IN ('PENDING', 'SUCCESS', 'FAILED'))
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `resume_version_id` | Nullable FK. A cover letter may be generated independently of a specific resume version, or linked to one for context. |

**JPA entity:** `tailoring.CoverLetter`

---

### 3.9 `scoring_results`

**Module owner:** `scoring`
**Zone:** Private

```sql
CREATE TABLE scoring_results (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id),
    resume_version_id   BIGINT          NOT NULL REFERENCES resume_versions(id),
    job_posting_id      BIGINT          NOT NULL REFERENCES job_postings(id),
    coverage_score      NUMERIC(4,3)    NOT NULL,   -- 0.000 to 1.000
    missing_required    JSONB           NOT NULL DEFAULT '[]',
    missing_preferred   JSONB           NOT NULL DEFAULT '[]',
    near_misses         JSONB           NOT NULL DEFAULT '[]',
    fixes               JSONB           NOT NULL DEFAULT '[]',
    scored_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_scoring_results_resume_job
        UNIQUE (resume_version_id, job_posting_id),

    CONSTRAINT ck_scoring_results_coverage_score
        CHECK (coverage_score BETWEEN 0.000 AND 1.000)
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `coverage_score` | `NUMERIC(4,3)` gives three decimal places of precision (e.g., `0.847`) without floating-point rounding issues. |
| `missing_required`, `missing_preferred`, `near_misses`, `fixes` | `JSONB` arrays. These are computed artifacts consumed as a unit by the gap report UI. Storing as JSONB avoids four child tables for data that is never queried element-by-element at the DB level. |
| Unique constraint | One scoring result per (resume, job) pair. Re-scoring overwrites the existing row via `INSERT ... ON CONFLICT DO UPDATE`. |

**Append-only in practice:** The unique constraint means re-scoring upserts rather than appends, but there is no `deleted_at` because scoring results are not user-deletable.

**JPA entity:** `scoring.ScoringResult`

---

### 3.10 `applications`

**Module owner:** `tracker`
**Zone:** Private

```sql
CREATE TABLE applications (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id),
    job_posting_id      BIGINT          NULL REFERENCES job_postings(id),
    resume_version_id   BIGINT          NULL REFERENCES resume_versions(id),
    company_name        TEXT            NOT NULL,   -- denormalized; job may be deleted
    job_title           TEXT            NOT NULL,   -- denormalized; same reason
    status              TEXT            NOT NULL DEFAULT 'SAVED',
    applied_at          TIMESTAMPTZ     NULL,
    notes               TEXT            NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ     NULL,

    CONSTRAINT ck_applications_status CHECK (
        status IN (
            'SAVED', 'APPLIED', 'PHONE_SCREEN', 'INTERVIEW',
            'OFFER', 'REJECTED', 'WITHDRAWN', 'CLOSED'
        )
    ),
    CONSTRAINT ck_applications_applied_at CHECK (
        (status = 'SAVED' AND applied_at IS NULL)
     OR (status != 'SAVED' AND applied_at IS NOT NULL)
    )
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `job_posting_id` | Nullable FK. A user may track an application to a job not in the system (e.g., applied directly via company website). |
| `resume_version_id` | Nullable FK. Records which resume version was submitted, if known. |
| `company_name`, `job_title` | Denormalized from `job_postings` at creation time. This ensures the tracker remains accurate even if the job posting is later deactivated or deleted. |
| `status` | Drives the state machine defined in §5.1 of the system design. The check constraint is the authoritative list of valid states. |
| `applied_at` | The consistency check constraint enforces that `applied_at` is set when and only when the application has moved past `SAVED`. |

**JPA entity:** `tracker.Application`

---

### 3.11 `interview_logs`

**Module owner:** `tracker`
**Zone:** Private (most sensitive table in the schema)

```sql
CREATE TABLE interview_logs (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id),
    application_id      BIGINT          NOT NULL REFERENCES applications(id),
    round_type          TEXT            NOT NULL,   -- 'PHONE', 'TECHNICAL', 'BEHAVIORAL', 'ONSITE', 'OTHER'
    interview_date      DATE            NULL,
    interviewer_name    TEXT            NULL,       -- PII; never leaves this table
    interviewer_role    TEXT            NULL,
    questions_asked     TEXT[]          NOT NULL DEFAULT '{}',
    notes               TEXT            NULL,       -- free text; PII risk
    outcome             TEXT            NULL,       -- 'PASSED', 'FAILED', 'PENDING', 'CANCELLED'
    difficulty          INTEGER         NULL,       -- 1–5 self-rating
    contributed_at      TIMESTAMPTZ     NULL,       -- non-null = contributed to public pool
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ     NULL,

    CONSTRAINT ck_interview_logs_round_type CHECK (
        round_type IN ('PHONE', 'TECHNICAL', 'BEHAVIORAL', 'ONSITE', 'OTHER')
    ),
    CONSTRAINT ck_interview_logs_outcome CHECK (
        outcome IS NULL
     OR outcome IN ('PASSED', 'FAILED', 'PENDING', 'CANCELLED')
    ),
    CONSTRAINT ck_interview_logs_difficulty CHECK (
        difficulty IS NULL OR difficulty BETWEEN 1 AND 5
    )
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `user_id` | Redundant with `application_id → applications.user_id`, but included explicitly so that every ownership check can be a single-table query without a join. |
| `interviewer_name` | PII. This field is in `PERSONAL_IDENTIFIER_FIELDS` and is stripped by `AnonymizationService` before any data leaves this table. It is never included in any API response other than to the owning user. |
| `notes` | Free text; high PII risk. Passed through `PiiRedactionService` during anonymization. |
| `contributed_at` | Non-null timestamp means this log has been contributed to the public pool. The `AnonymizationService` sets this field atomically with the write to `company_interview_intel`. |

**JPA entity:** `tracker.InterviewLog`

**Access rule:** No query against `interview_logs` may omit a `WHERE user_id = :userId` clause. This is enforced by ArchUnit: all `InterviewLogRepository` methods must include `userId` as a parameter.

---

### 3.12 `company_interview_intel`

**Module owner:** `intel` (read); `shared.anonymization` (write)
**Zone:** Public shared pool

```sql
CREATE TABLE company_interview_intel (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_name        TEXT            NOT NULL,
    role_category       TEXT            NOT NULL,   -- normalized role bucket, not exact title
    round_type          TEXT            NOT NULL,
    questions           TEXT[]          NOT NULL DEFAULT '{}',
    process_notes       TEXT            NULL,
    difficulty          INTEGER         NULL,       -- 1–5; averaged at read time
    source_log_count    INTEGER         NOT NULL DEFAULT 1,  -- how many logs contributed to this record
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_company_intel_round_type CHECK (
        round_type IN ('PHONE', 'TECHNICAL', 'BEHAVIORAL', 'ONSITE', 'OTHER')
    ),
    CONSTRAINT ck_company_intel_difficulty CHECK (
        difficulty IS NULL OR difficulty BETWEEN 1 AND 5
    )
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `company_name` | The only identifier. No user ID, no application ID, no personal data of any kind. |
| `role_category` | A normalized bucket (e.g., `"Software Engineer"`, `"Product Manager"`) rather than the exact job title from the application. Normalization is performed by `AnonymizationService` to prevent re-identification via rare job titles. |
| `questions` | The anonymized and PII-scrubbed questions array from the source `interview_log`. |
| `source_log_count` | Records how many individual interview logs contributed to this record. Used by the UI to indicate data volume and by `AnonymizationService` to enforce the minimum aggregation threshold. |

**No `user_id`, no `deleted_at`:** This table contains no personal data by design. Rows are never deleted; they are the permanent public record.

**Write access:** Only `AnonymizationService` (in `shared.anonymization`) calls `CompanyIntelRepository.save()`. The repository's `save()` method is package-private. `IntelService` uses a separate read-only repository interface.

**JPA entity:** `intel.CompanyInterviewIntel`

---

### 3.13 `company_intel_staging`

**Module owner:** `shared.anonymization`
**Zone:** Public shared pool (pre-publication buffer)

```sql
CREATE TABLE company_intel_staging (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_name        TEXT            NOT NULL,
    role_category       TEXT            NOT NULL,
    round_type          TEXT            NOT NULL,
    questions           TEXT[]          NOT NULL DEFAULT '{}',
    process_notes       TEXT            NULL,
    difficulty          INTEGER         NULL,
    source_interview_log_id BIGINT      NOT NULL,   -- FK to interview_logs; kept for promotion logic
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_staging_round_type CHECK (
        round_type IN ('PHONE', 'TECHNICAL', 'BEHAVIORAL', 'ONSITE', 'OTHER')
    )
);
```

**Column notes:**

| Column | Rationale |
|---|---|
| `source_interview_log_id` | FK back to `interview_logs`. This is the only table in Zone 3 that carries a reference to private data, and it is intentional: the staging table is not public-facing. When the threshold is met and the record is promoted to `company_interview_intel`, this FK is used to mark the source log as `