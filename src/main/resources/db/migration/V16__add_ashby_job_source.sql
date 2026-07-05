-- Add the Ashby provenance to the job_source enum. PostgreSQL 16 allows ALTER TYPE ... ADD VALUE
-- inside a transaction as long as the new value is not used in the same transaction (it is not here).
-- Kept in its own migration (V5 precedent) so the value is committed before any later use.
ALTER TYPE job_source ADD VALUE IF NOT EXISTS 'ASHBY';
