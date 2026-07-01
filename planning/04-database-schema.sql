-- =============================================================================
-- Career Copilot — Production Database Schema
-- PostgreSQL 16 + pgvector extension
-- =============================================================================
--
-- MIGRATION STRUCTURE
-- -------------------
-- This file represents the canonical baseline schema (V1__baseline.sql in
-- Flyway / Liquibase terms).  Every subsequent change is a numbered migration:
--
--   V1__baseline.sql                  ← this file
--   V2__add_jwt_denylist_index.sql
--   V3__staging_threshold_column.sql
--   ...
--
-- Naming conventions
--   • Tables:   snake_case, plural nouns
--   • Columns:  snake_case
--   • PKs:      id  BIGSERIAL (surrogate)
--   • FKs:      <referenced_table_singular>_id
--   • Indexes:  idx_<table>_<column(s)>
--   • Unique:   uq_<table>_<column(s)>
--   • Checks:   ck_<table>_<column>
--
-- Data zones
--   PRIVATE  — rows are always scoped to a single user_id; never exposed
--              to other users or public endpoints
--   SHARED   — anonymised, aggregated; readable without authentication
--   SYSTEM   — infrastructure / audit tables; no user-visible data
--
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Prerequisites
-- ---------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS pgvector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- trigram indexes for ILIKE search

-- ---------------------------------------------------------------------------
-- ENUM TYPES
-- ---------------------------------------------------------------------------

CREATE TYPE application_status AS ENUM (
    'SAVED',
    'APPLIED',
    'PHONE_SCREEN',
    'TECHNICAL_INTERVIEW',
    'FINAL_ROUND',
    'OFFER',
    'REJECTED',
    'WITHDRAWN'
);

CREATE TYPE interview_round_type AS ENUM (
    'PHONE_SCREEN',
    'TECHNICAL',
    'SYSTEM_DESIGN',
    'BEHAVIOURAL',
    'TAKE_HOME',
    'PANEL',
    'HR',
    'OTHER'
);

CREATE TYPE contribution_status AS ENUM (
    'STAGED',
    'PUBLISHED'
);

CREATE TYPE llm_task AS ENUM (
    'EXTRACTION',
    'TAILORING',
    'SCORING_AUGMENTATION',
    'COVER_LETTER'
);

CREATE TYPE job_source AS ENUM (
    'ADZUNA',
    'REMOTEOK',
    'USAJOBS',
    'MANUAL_PASTE'
);

-- =============================================================================
-- ZONE: SYSTEM
-- =============================================================================

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
-- Central identity record.  Password is stored as a BCrypt hash (cost 12).
-- email is the login credential; it is normalised to lower-case at the
-- application layer before persistence.
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id                  BIGSERIAL       PRIMARY KEY,
    email               TEXT            NOT NULL,
    password_hash       TEXT            NOT NULL,
    full_name           TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

COMMENT ON TABLE  users                IS 'SYSTEM — one row per registered user; owns all PRIVATE data via FK';
COMMENT ON COLUMN users.password_hash  IS 'BCrypt hash, cost 12; never returned by any API';
COMMENT ON COLUMN users.email          IS 'Normalised to lower-case before INSERT/UPDATE';

CREATE INDEX idx_users_email ON users (email);

-- ---------------------------------------------------------------------------
-- jwt_denylist
-- ---------------------------------------------------------------------------
-- Stores revoked JWT JTIs (e.g. after logout).  The Spring Security filter
-- checks this table on every authenticated request.  Rows are safe to purge
-- once expires_at < now(); a nightly job handles this.
-- ---------------------------------------------------------------------------

CREATE TABLE jwt_denylist (
    jti                 TEXT            PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    revoked_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ     NOT NULL
);

COMMENT ON TABLE jwt_denylist IS 'SYSTEM — revoked JWT identifiers; purge rows where expires_at < now()';

CREATE INDEX idx_jwt_denylist_expires_at ON jwt_denylist (expires_at);
CREATE INDEX idx_jwt_denylist_user_id    ON jwt_denylist (user_id);

-- ---------------------------------------------------------------------------
-- token_usage_log
-- ---------------------------------------------------------------------------
-- Append-only audit of every LLM call.  Used for cost attribution, anomaly
-- detection, and the /api/v1/metrics/ats-improvement endpoint.
-- ---------------------------------------------------------------------------

CREATE TABLE token_usage_log (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          REFERENCES users (id) ON DELETE SET NULL,
    task                llm_task        NOT NULL,
    model_name          TEXT            NOT NULL,
    prompt_tokens       INT             NOT NULL CHECK (prompt_tokens >= 0),
    completion_tokens   INT             NOT NULL CHECK (completion_tokens >= 0),
    total_tokens        INT             NOT NULL
                            GENERATED ALWAYS AS (prompt_tokens + completion_tokens) STORED,
    latency_ms          INT             CHECK (latency_ms >= 0),
    called_at           TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE token_usage_log IS 'SYSTEM — append-only LLM call audit; never updated';

CREATE INDEX idx_token_usage_log_user_id   ON token_usage_log (user_id);
CREATE INDEX idx_token_usage_log_called_at ON token_usage_log (called_at);
CREATE INDEX idx_token_usage_log_task      ON token_usage_log (task);

-- =============================================================================
-- ZONE: PRIVATE — job postings (shared read, but not user-scoped content)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- job_postings
-- ---------------------------------------------------------------------------
-- Normalised job postings ingested from external sources or pasted manually.
-- Readable by all authenticated users (and unauthenticated for browse/search).
-- The pasted_by_user_id column is set only for MANUAL_PASTE rows; it is used
-- for rate-limiting and abuse detection, not for data scoping.
-- ---------------------------------------------------------------------------

CREATE TABLE job_postings (
    id                  BIGSERIAL       PRIMARY KEY,
    source              job_source      NOT NULL,
    external_id         TEXT,                           -- source-assigned ID; NULL for manual
    title               TEXT            NOT NULL,
    company             TEXT            NOT NULL,
    location            TEXT,
    remote              BOOLEAN         NOT NULL DEFAULT false,
    employment_type     TEXT,                           -- 'FULL_TIME', 'CONTRACT', etc.
    salary_min          NUMERIC(12, 2),
    salary_max          NUMERIC(12, 2),
    salary_currency     CHAR(3),
    skills              TEXT[]          NOT NULL DEFAULT '{}',
    responsibilities    TEXT[]          NOT NULL DEFAULT '{}',
    full_text           TEXT            NOT NULL,
    url                 TEXT,
    posted_at           TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    pasted_by_user_id   BIGINT          REFERENCES users (id) ON DELETE SET NULL,
    ingested_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
    is_active           BOOLEAN         NOT NULL DEFAULT true,

    CONSTRAINT uq_job_postings_source_external UNIQUE (source, external_id),
    CONSTRAINT ck_job_postings_salary CHECK (
        salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max
    )
);

COMMENT ON TABLE  job_postings                   IS 'Normalised job postings; readable without auth; MANUAL_PASTE rows linked to submitting user';
COMMENT ON COLUMN job_postings.external_id       IS 'NULL for MANUAL_PASTE; unique per source otherwise';
COMMENT ON COLUMN job_postings.skills            IS 'Extracted skill tokens; used by KeywordExtractor';
COMMENT ON COLUMN job_postings.full_text         IS 'Raw normalised text; used for embedding and keyword extraction';
COMMENT ON COLUMN job_postings.pasted_by_user_id IS 'PRIVATE ref — only set for MANUAL_PASTE; not exposed in public listing';

CREATE INDEX idx_job_postings_company        ON job_postings (company);
CREATE INDEX idx_job_postings_is_active      ON job_postings (is_active);
CREATE INDEX idx_job_postings_ingested_at    ON job_postings (ingested_at DESC);
CREATE INDEX idx_job_postings_posted_at      ON job_postings (posted_at DESC);
CREATE INDEX idx_job_postings_source         ON job_postings (source);

-- Full-text search index over title + company + full_text
CREATE INDEX idx_job_postings_fts ON job_postings
    USING GIN (to_tsvector('english', coalesce(title, '') || ' ' ||
                                       coalesce(company, '') || ' ' ||
                                       coalesce(full_text, '')));

-- Trigram index for ILIKE company/title search
CREATE INDEX idx_job_postings_title_trgm   ON job_postings USING GIN (title   gin_trgm_ops);
CREATE INDEX idx_job_postings_company_trgm ON job_postings USING GIN (company gin_trgm_ops);

-- =============================================================================
-- ZONE: PRIVATE — candidate profile & experience corpus
-- =============================================================================

-- ---------------------------------------------------------------------------
-- candidate_profiles
-- ---------------------------------------------------------------------------
-- One row per user.  Created lazily on first profile PUT.
-- headline and summary are used as context in the tailoring prompt.
-- ---------------------------------------------------------------------------

CREATE TABLE candidate_profiles (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
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

COMMENT ON TABLE candidate_profiles IS 'PRIVATE — one-to-one with users; profile metadata used in tailoring prompts';

CREATE INDEX idx_candidate_profiles_user_id ON candidate_profiles (user_id);

-- ---------------------------------------------------------------------------
-- experience_entries
-- ---------------------------------------------------------------------------
-- The candidate's experience corpus.  Each entry is independently embedded;
-- the embedding column is populated asynchronously after INSERT/UPDATE.
-- text_hash stores SHA-256(role || company || description || achievements)
-- so ProfileService can detect whether re-embedding is needed on UPDATE.
-- ---------------------------------------------------------------------------

CREATE TABLE experience_entries (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role                TEXT            NOT NULL,
    company             TEXT            NOT NULL,
    location            TEXT,
    start_date          DATE            NOT NULL,
    end_date            DATE,                           -- NULL = current position
    is_current          BOOLEAN         NOT NULL DEFAULT false,
    description         TEXT,
    achievements        TEXT[]          NOT NULL DEFAULT '{}',
    skills_used         TEXT[]          NOT NULL DEFAULT '{}',
    embedding           vector(1536),                   -- OpenAI text-embedding-3-small dim
    text_hash           CHAR(64),                       -- SHA-256 hex; used to detect text changes
    embedded_at         TIMESTAMPTZ,                    -- NULL until first embedding succeeds
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_experience_entries_dates CHECK (
        end_date IS NULL OR end_date >= start_date
    ),
    CONSTRAINT ck_experience_entries_current CHECK (
        NOT (is_current = true AND end_date IS NOT NULL)
    )
);

COMMENT ON TABLE  experience_entries           IS 'PRIVATE — candidate experience corpus; each row independently embedded for RAG retrieval';
COMMENT ON COLUMN experience_entries.embedding IS 'pgvector 1536-dim; NULL until EmbeddingService populates it';
COMMENT ON COLUMN experience_entries.text_hash IS 'SHA-256 of concatenated text fields; ProfileService re-embeds when hash changes';

CREATE INDEX idx_experience_entries_user_id    ON experience_entries (user_id);
CREATE INDEX idx_experience_entries_embedded_at ON experience_entries (embedded_at)
    WHERE embedded_at IS NULL;   -- partial index: quickly find un-embedded rows

-- pgvector HNSW index for approximate nearest-neighbour search
-- Used by: ExperienceRepository.findTopKByVector(userId, queryVector, k)
CREATE INDEX idx_experience_entries_embedding_hnsw
    ON experience_entries
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- =============================================================================
-- ZONE: PRIVATE — resume tailoring outputs
-- =============================================================================

-- ---------------------------------------------------------------------------
-- resume_versions
-- ---------------------------------------------------------------------------
-- One row per tailoring run.  json_payload stores the structured LLM output
-- (ResumeJsonPayload) as JSONB; pdf_bytes stores the rendered PDF.
-- pdf_bytes may be NULL if Tectonic failed — the JSON is always persisted
-- (REQUIRES_NEW transaction in TailoringService) so PDF generation can be
-- retried without re-invoking the LLM.
-- ---------------------------------------------------------------------------

CREATE TABLE resume_versions (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_posting_id      BIGINT          NOT NULL REFERENCES job_postings (id) ON DELETE RESTRICT,
    template_id         TEXT            NOT NULL,
    json_payload        JSONB           NOT NULL,
    pdf_bytes           BYTEA,                          -- NULL if rendering failed; retryable
    llm_model           TEXT            NOT NULL,
    token_count         INT             NOT NULL CHECK (token_count >= 0),
    render_failed       BOOLEAN         NOT NULL DEFAULT false,
    render_error        TEXT,                           -- Tectonic stderr on failure
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  resume_versions              IS 'PRIVATE — one row per tailoring run; JSON always persisted; PDF may be NULL on render failure';
COMMENT ON COLUMN resume_versions.json_payload IS 'ResumeJsonPayload as JSONB; source of truth for re-render';
COMMENT ON COLUMN resume_versions.pdf_bytes    IS 'Rendered PDF; NULL if Tectonic failed; re-renderable from json_payload';
COMMENT ON COLUMN resume_versions.render_failed IS 'true when Tectonic exited non-zero or timed out';

CREATE INDEX idx_resume_versions_user_id        ON resume_versions (user_id);
CREATE INDEX idx_resume_versions_job_posting_id ON resume_versions (job_posting_id);
CREATE INDEX idx_resume_versions_created_at     ON resume_versions (user_id, created_at DESC);
CREATE INDEX idx_resume_versions_render_failed  ON resume_versions (render_failed)
    WHERE render_failed = true;  -- partial index: quickly find failed renders for retry

-- ---------------------------------------------------------------------------
-- cover_letters
-- ---------------------------------------------------------------------------

CREATE TABLE cover_letters (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_posting_id      BIGINT          NOT NULL REFERENCES job_postings (id) ON DELETE RESTRICT,
    resume_version_id   BIGINT          REFERENCES resume_versions (id) ON DELETE SET NULL,
    template_id         TEXT            NOT NULL,
    json_payload        JSONB           NOT NULL,
    pdf_bytes           BYTEA,
    llm_model           TEXT            NOT NULL,
    token_count         INT             NOT NULL CHECK (token_count >= 0),
    render_failed       BOOLEAN         NOT NULL DEFAULT false,
    render_error        TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE cover_letters IS 'PRIVATE — generated cover letters; mirrors resume_versions structure';

CREATE INDEX idx_cover_letters_user_id        ON cover_letters (user_id);
CREATE INDEX idx_cover_letters_job_posting_id ON cover_letters (job_posting_id);
CREATE INDEX idx_cover_letters_created_at     ON cover_letters (user_id, created_at DESC);

-- =============================================================================
-- ZONE: PRIVATE — ATS scoring results
-- =============================================================================

-- ---------------------------------------------------------------------------
-- scoring_results
-- ---------------------------------------------------------------------------
-- One row per scoring run.  missing_required, missing_preferred, near_misses,
-- and fixes are stored as JSONB arrays for flexible querying and to avoid
-- a proliferation of child tables for what is essentially a report snapshot.
-- ---------------------------------------------------------------------------

CREATE TABLE scoring_results (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    resume_version_id   BIGINT          NOT NULL REFERENCES resume_versions (id) ON DELETE CASCADE,
    job_posting_id      BIGINT          NOT NULL REFERENCES job_postings (id) ON DELETE RESTRICT,
    coverage_score      NUMERIC(5, 4)   NOT NULL
                            CHECK (coverage_score >= 0 AND coverage_score <= 1),
    missing_required    JSONB           NOT NULL DEFAULT '[]',
    missing_preferred   JSONB           NOT NULL DEFAULT '[]',
    near_misses         JSONB           NOT NULL DEFAULT '[]',
    fixes               JSONB           NOT NULL DEFAULT '[]',
    scored_at           TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  scoring_results                IS 'PRIVATE — ATS gap analysis snapshot per resume version';
COMMENT ON COLUMN scoring_results.coverage_score IS '0.0000–1.0000; fraction of required skills covered';
COMMENT ON COLUMN scoring_results.near_misses    IS 'JSONB array of {skill, similarity, suggestedPhrase}';
COMMENT ON COLUMN scoring_results.fixes          IS 'JSONB array of {skill, suggestedBullet, priority}';

CREATE INDEX idx_scoring_results_user_id          ON scoring_results (user_id);
CREATE INDEX idx_scoring_results_resume_version   ON scoring_results (resume_version_id);
CREATE INDEX idx_scoring_results_scored_at        ON scoring_results (user_id, scored_at DESC);

-- =============================================================================
-- ZONE: PRIVATE — application tracker
-- =============================================================================

-- ---------------------------------------------------------------------------
-- applications
-- ---------------------------------------------------------------------------
-- One row per job application tracked by a user.  status drives the
-- ApplicationStatus state machine defined in §5.1 of the system design.
-- resume_version_id and cover_letter_id are optional links to generated
-- documents; they may be NULL if the user applied with an external document.
-- ---------------------------------------------------------------------------

CREATE TABLE applications (
    id                  BIGSERIAL           PRIMARY KEY,
    user_id             BIGINT              NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_posting_id      BIGINT              REFERENCES job_postings (id) ON DELETE SET NULL,
    -- Denormalised snapshot in case the job_posting is later deleted
    job_title_snapshot  TEXT                NOT NULL,
    company_snapshot    TEXT                NOT NULL,
    resume_version_id   BIGINT              REFERENCES resume_versions (id) ON DELETE SET NULL,
    cover_letter_id     BIGINT              REFERENCES cover_letters (id) ON DELETE SET NULL,
    status              application_status  NOT NULL DEFAULT 'SAVED',
    applied_at          TIMESTAMPTZ,
    notes               TEXT,
    source_url          TEXT,
    created_at          TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ         NOT NULL DEFAULT now()
);

COMMENT ON TABLE  applications                    IS 'PRIVATE — one row per tracked application; drives ApplicationStatus state machine';
COMMENT ON COLUMN applications.job_title_snapshot IS 'Denormalised at creation time; preserved if job_posting is deleted';
COMMENT ON COLUMN applications.company_snapshot   IS 'Denormalised at creation time; preserved if job_posting is deleted';

CREATE INDEX idx_applications_user_id        ON applications (user_id);
CREATE INDEX idx_applications_status         ON applications (user_id, status);
CREATE INDEX idx_applications_job_posting_id ON applications (job_posting_id);
CREATE INDEX idx_applications_applied_at     ON applications (user_id, applied_at DESC);

-- ---------------------------------------------------------------------------
-- interview_logs
-- ---------------------------------------------------------------------------
-- PRIVATE interview notes attached to an application.  These rows are the
-- source material for the anonymisation pipeline; they are NEVER exposed
-- directly to other users or public endpoints.
-- contributed_at is set by AnonymizationService.markContributed() once the
-- anonymised record has been written to company_interview_intel (or staged).
-- ---------------------------------------------------------------------------

CREATE TABLE interview_logs (
    id                  BIGSERIAL           PRIMARY KEY,
    application_id      BIGINT              NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    user_id             BIGINT              NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    round_type          interview_round_type NOT NULL,
    round_number        SMALLINT            NOT NULL DEFAULT 1 CHECK (round_number >= 1),
    interview_date      DATE,
    duration_minutes    SMALLINT            CHECK (duration_minutes > 0),
    interviewer_role    TEXT,               -- e.g. "Senior Engineer" — no name
    questions_asked     TEXT[]              NOT NULL DEFAULT '{}',
    notes               TEXT,
    feedback_received   TEXT,
    outcome             TEXT,               -- 'PASSED', 'FAILED', 'PENDING', 'UNKNOWN'
    difficulty          SMALLINT            CHECK (difficulty BETWEEN 1 AND 5),
    contributed_at      TIMESTAMPTZ,        -- NULL until AnonymizationService publishes/stages
    contribution_status contribution_status,
    created_at          TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ         NOT NULL DEFAULT now()
);

COMMENT ON TABLE  interview_logs                    IS 'PRIVATE — raw interview notes; source for anonymisation pipeline; never exposed to other users';
COMMENT ON COLUMN interview_logs.interviewer_role   IS 'Role title only — no personal name stored';
COMMENT ON COLUMN interview_logs.contributed_at     IS 'Set by AnonymizationService; NULL = not yet contributed';
COMMENT ON COLUMN interview_logs.contribution_status IS 'STAGED = below threshold; PUBLISHED = in company_interview_intel';

CREATE INDEX idx_interview_logs_application_id  ON interview_logs (application_id);
CREATE INDEX idx_interview_logs_user_id         ON interview_logs (user_id);
CREATE INDEX idx_interview_logs_contributed_at  ON interview_logs (contributed_at)
    WHERE contributed_at IS NULL;  -- partial index: find un-contributed logs

-- =============================================================================
-- ZONE: SHARED — public company interview intelligence pool
-- =============================================================================

-- ---------------------------------------------------------------------------
-- company_interview_intel
-- ---------------------------------------------------------------------------
-- Anonymised, aggregated interview intelligence.  Rows are written ONLY by
-- AnonymizationService; no other code path may INSERT or UPDATE this table.
-- There is no user_id column and no application_id column — by design.
-- The staging_batch_id column links rows that are waiting for the minimum
-- aggregation threshold to be met before they become publicly visible.
-- ---------------------------------------------------------------------------

CREATE TABLE company_interview_intel (
    id                  BIGSERIAL       PRIMARY KEY,
    company             TEXT            NOT NULL,
    role_category       TEXT,                           -- normalised role family, e.g. "Software Engineer"
    round_type          interview_round_type NOT NULL,
    questions           TEXT[]          NOT NULL DEFAULT '{}',
    process_notes       TEXT,
    difficulty          SMALLINT        CHECK (difficulty BETWEEN 1 AND 5),
    is_published        BOOLEAN         NOT NULL DEFAULT false,
    staging_batch_id    UUID,                           -- groups staged rows for the same company
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  company_interview_intel             IS 'SHARED — anonymised interview intel; written only by AnonymizationService; no PII columns';
COMMENT ON COLUMN company_interview_intel.is_published IS 'false = staged (below threshold); true = publicly visible';
COMMENT ON COLUMN company_interview_intel.staging_batch_id IS 'Groups staged rows; set to NULL after publishing';

CREATE INDEX idx_company_intel_company      ON company_interview_intel (company);
CREATE INDEX idx_company_intel_published    ON company_interview_intel (company, is_published)
    WHERE is_published = true;
CREATE INDEX idx_company_intel_staging      ON company_interview_intel (staging_batch_id)
    WHERE is_published = false;
CREATE INDEX idx_company_intel_role         ON company_interview_intel (company, role_category)
    WHERE is_published = true;

-- Trigram index for company name search
CREATE INDEX idx_company_intel_company_trgm ON company_interview_intel
    USING GIN (company gin_trgm_ops);

-- =============================================================================
-- UPDATED_AT TRIGGERS
-- =============================================================================
-- Automatically maintain updated_at on every table that carries it.

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_candidate_profiles_updated_at
    BEFORE UPDATE ON candidate_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_experience_entries_updated_at
    BEFORE UPDATE ON experience_entries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_applications_updated_at
    BEFORE UPDATE ON applications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_interview_logs_updated_at
    BEFORE UPDATE ON interview_logs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- ROW-LEVEL SECURITY (baseline policies)
-- =============================================================================
-- RLS is a defence-in-depth layer.  The application layer enforces ownership
-- via WHERE user_id = :userId in every query; RLS provides a second barrier
-- in case of a query construction bug.
--
-- The application connects as the role 'app_user'.
-- Migrations run as the role 'migrator' (superuser or schema owner) which
-- bypasses RLS.

-- Enable RLS on all PRIVATE tables
ALTER TABLE candidate_profiles      ENABLE ROW LEVEL SECURITY;
ALTER TABLE experience_entries      ENABLE ROW LEVEL SECURITY;
ALTER TABLE resume_versions         ENABLE ROW LEVEL SECURITY;
ALTER TABLE cover_letters           ENABLE ROW LEVEL SECURITY;
ALTER TABLE scoring_results         ENABLE ROW LEVEL SECURITY;
ALTER TABLE applications            ENABLE ROW LEVEL SECURITY;
ALTER TABLE interview_logs          ENABLE ROW LEVEL SECURITY;
ALTER TABLE token_usage_log         ENABLE ROW LEVEL SECURITY;

-- The application sets this at the start of every connection/transaction:
--   SET LOCAL app.current_user_id = '<userId>';
-- Policies use current_setting('app.current_user_id', true) to read it.

CREATE POLICY pol_candidate_profiles_owner
    ON candidate_profiles
    USING (user_id = current_setting('app.current_user_id', true)::BIGINT);

CREATE POLICY pol_experience_entries_owner
    ON experience_entries
    USING (user_id = current_setting('app.current_user_id', true)::BIGINT);

CREATE POLICY pol_resume_versions_owner
    ON resume_versions
    USING (user_id = current_setting('app.current_user_id', true)::BIGINT);

CREATE POLICY pol_cover_letters_owner
    ON cover_letters
    USING (user_id = current_setting('app.current_user_id', true)::BIGINT);

CREATE POLICY pol_scoring_results_owner
    ON scoring_results
    USING (user_id = current_setting('app.current_user_id', true)::BIGINT);

CREATE POLICY pol_applications_owner
    ON applications
    USING (user_id = current_setting('app.current_user_id', true)::BIGINT);

CREATE POLICY pol_interview_logs_owner
    ON interview_logs
    USING (user_id = current_setting('app.current_user_id', true)::BIGINT);

CREATE POLICY pol_token_usage_log_owner
    ON token_usage_log
    USING (
        user_id IS NULL
        OR user_id = current_setting('app.current_user_id', true)::BIGINT
    );

-- company_interview_intel is SHARED — no RLS; all rows are public once published.
-- jwt_denylist is read by the security filter before user context is set — no RLS.
-- job_postings is public read — no RLS.
-- users is accessed only by AuthService with explicit WHERE id = ? — no RLS.

-- =============================================================================
-- DATABASE ROLES
-- =============================================================================

-- app_user: the role used by the Spring Boot connection pool at runtime.
-- It has no superuser privileges and cannot ALTER or DROP schema objects.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user LOGIN;
    END IF;
END;
$$;

GRANT CONNECT ON DATABASE postgres TO app_user;  -- replace 'postgres' with actual DB name in migration
GRANT USAGE  ON SCHEMA public TO app_user;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    users,
    jwt_denylist,
    token_usage_log,
    job_postings,
    candidate_profiles,
    experience_entries,
    resume_versions,
    cover_letters,
    scoring_results,
    applications,
    interview_logs,
    company_interview_intel
TO app_user;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;

-- =============================================================================
-- END OF BASELINE SCHEMA  (V1__baseline.sql)
-- =============================================================================