-- SHARED zone: no user_id, no application_id — by design. Written only by AnonymizationService.
CREATE TABLE company_interview_intel (
    id               BIGSERIAL            PRIMARY KEY,
    company          TEXT                 NOT NULL,
    role_category    TEXT,
    round_type       interview_round_type NOT NULL,
    questions        TEXT[]               NOT NULL DEFAULT '{}',
    process_notes    TEXT,
    difficulty       SMALLINT             CHECK (difficulty BETWEEN 1 AND 5),
    is_published     BOOLEAN              NOT NULL DEFAULT false,
    staging_batch_id UUID,
    published_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ          NOT NULL DEFAULT now()
);

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_company_intel_company   ON company_interview_intel (company);
CREATE INDEX idx_company_intel_published ON company_interview_intel (company, is_published) WHERE is_published = true;
CREATE INDEX idx_company_intel_staging   ON company_interview_intel (staging_batch_id)      WHERE is_published = false;
CREATE INDEX idx_company_intel_role      ON company_interview_intel (company, role_category) WHERE is_published = true;
CREATE INDEX idx_company_intel_company_trgm ON company_interview_intel USING GIN (company gin_trgm_ops);
