CREATE TYPE application_status AS ENUM (
    'SAVED','APPLIED','PHONE_SCREEN','TECHNICAL_INTERVIEW','FINAL_ROUND','OFFER','REJECTED','WITHDRAWN'
);

CREATE TABLE applications (
    id                 BIGSERIAL          PRIMARY KEY,
    user_id            UUID               NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_posting_id     BIGINT             REFERENCES job_postings (id) ON DELETE SET NULL,
    job_title_snapshot TEXT               NOT NULL,
    company_snapshot   TEXT               NOT NULL,
    resume_version_id  BIGINT             REFERENCES resume_versions (id) ON DELETE SET NULL,
    cover_letter_id    BIGINT             REFERENCES cover_letters (id) ON DELETE SET NULL,
    status             application_status NOT NULL DEFAULT 'SAVED',
    applied_at         TIMESTAMPTZ,
    notes              TEXT,
    source_url         TEXT,
    created_at         TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ        NOT NULL DEFAULT now()
);
CREATE INDEX idx_applications_user_id        ON applications (user_id);
CREATE INDEX idx_applications_status         ON applications (user_id, status);
CREATE INDEX idx_applications_job_posting_id ON applications (job_posting_id);
CREATE INDEX idx_applications_applied_at     ON applications (user_id, applied_at DESC);
