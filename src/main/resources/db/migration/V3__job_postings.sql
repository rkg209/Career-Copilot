CREATE TYPE job_source AS ENUM ('ADZUNA', 'REMOTEOK', 'USAJOBS', 'MANUAL_PASTE');

CREATE TABLE job_postings (
    id                  BIGSERIAL       PRIMARY KEY,
    source              job_source      NOT NULL,
    external_id         TEXT,
    title               TEXT            NOT NULL,
    company             TEXT            NOT NULL,
    location            TEXT,
    remote              BOOLEAN         NOT NULL DEFAULT false,
    employment_type     TEXT,
    salary_min          NUMERIC(12, 2),
    salary_max          NUMERIC(12, 2),
    salary_currency     VARCHAR(3),
    skills              TEXT[]          NOT NULL DEFAULT '{}',
    responsibilities    TEXT[]          NOT NULL DEFAULT '{}',
    full_text           TEXT            NOT NULL,
    url                 TEXT,
    posted_at           TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    pasted_by_user_id   UUID            REFERENCES users (id) ON DELETE SET NULL,
    ingested_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
    is_active           BOOLEAN         NOT NULL DEFAULT true,

    CONSTRAINT uq_job_postings_source_external UNIQUE (source, external_id),
    CONSTRAINT ck_job_postings_salary CHECK (
        salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max
    )
);

CREATE INDEX idx_job_postings_company     ON job_postings (company);
CREATE INDEX idx_job_postings_is_active   ON job_postings (is_active);
CREATE INDEX idx_job_postings_ingested_at ON job_postings (ingested_at DESC);
CREATE INDEX idx_job_postings_posted_at   ON job_postings (posted_at DESC);
CREATE INDEX idx_job_postings_source      ON job_postings (source);
