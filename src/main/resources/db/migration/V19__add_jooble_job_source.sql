-- Add the Jooble aggregator provenance to the job_source enum. PostgreSQL 16 allows
-- ALTER TYPE ... ADD VALUE inside a transaction as long as the value is not used in the same
-- transaction (it is not here). Kept in its own migration (V5/V16 precedent).
ALTER TYPE job_source ADD VALUE IF NOT EXISTS 'JOOBLE';
