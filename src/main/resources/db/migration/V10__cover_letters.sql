CREATE TABLE cover_letters (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_posting_id    BIGINT      NOT NULL REFERENCES job_postings (id) ON DELETE RESTRICT,
    resume_version_id BIGINT      REFERENCES resume_versions (id) ON DELETE SET NULL,
    template_id       TEXT        NOT NULL,
    json_payload      JSONB       NOT NULL,
    pdf_bytes         BYTEA,
    llm_model         TEXT        NOT NULL,
    token_count       INT         NOT NULL CHECK (token_count >= 0),
    render_failed     BOOLEAN     NOT NULL DEFAULT false,
    render_error      TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cover_letters_user_id        ON cover_letters (user_id);
CREATE INDEX idx_cover_letters_job_posting_id ON cover_letters (job_posting_id);
CREATE INDEX idx_cover_letters_created_at     ON cover_letters (user_id, created_at DESC);
