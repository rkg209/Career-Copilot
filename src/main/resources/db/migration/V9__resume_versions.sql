CREATE TABLE resume_versions (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_posting_id    BIGINT      NOT NULL REFERENCES job_postings (id) ON DELETE RESTRICT,
    template_id       TEXT        NOT NULL,
    json_payload      JSONB       NOT NULL,
    pdf_bytes         BYTEA,                                  -- NULL if render failed; retryable
    llm_model         TEXT        NOT NULL,
    token_count       INT         NOT NULL CHECK (token_count >= 0),
    render_failed     BOOLEAN     NOT NULL DEFAULT false,
    render_error      TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_resume_versions_user_id        ON resume_versions (user_id);
CREATE INDEX idx_resume_versions_job_posting_id ON resume_versions (job_posting_id);
CREATE INDEX idx_resume_versions_created_at     ON resume_versions (user_id, created_at DESC);
CREATE INDEX idx_resume_versions_render_failed  ON resume_versions (render_failed) WHERE render_failed = true;
