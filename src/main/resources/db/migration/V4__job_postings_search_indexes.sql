CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_job_postings_fts ON job_postings
    USING GIN (to_tsvector('english',
        coalesce(title, '') || ' ' || coalesce(company, '') || ' ' || coalesce(full_text, '')));

CREATE INDEX idx_job_postings_title_trgm   ON job_postings USING GIN (title   gin_trgm_ops);
CREATE INDEX idx_job_postings_company_trgm ON job_postings USING GIN (company gin_trgm_ops);
