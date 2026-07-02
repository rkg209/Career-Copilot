-- Phase 4: ATS gap-report snapshots, one row per score run.
CREATE TABLE scoring_results (
    id                BIGSERIAL     PRIMARY KEY,
    user_id           UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    resume_version_id BIGINT        NOT NULL REFERENCES resume_versions (id) ON DELETE CASCADE,
    job_posting_id    BIGINT        NOT NULL REFERENCES job_postings (id) ON DELETE RESTRICT,
    score             NUMERIC(5,2)  NOT NULL CHECK (score >= 0 AND score <= 100),
    breakdown         JSONB         NOT NULL DEFAULT '{}',
    missing_required  JSONB         NOT NULL DEFAULT '[]',
    missing_preferred JSONB         NOT NULL DEFAULT '[]',
    near_misses       JSONB         NOT NULL DEFAULT '[]',
    fixes             JSONB         NOT NULL DEFAULT '[]',
    scored_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_scoring_results_user_id        ON scoring_results (user_id);
CREATE INDEX idx_scoring_results_resume_version ON scoring_results (resume_version_id);
CREATE INDEX idx_scoring_results_scored_at      ON scoring_results (user_id, scored_at DESC);
