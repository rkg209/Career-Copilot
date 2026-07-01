-- Add ATS-board provenances to the job_source enum. PostgreSQL 16 allows ALTER TYPE ... ADD VALUE
-- inside a transaction as long as the new value is not used in the same transaction (it is not here).
ALTER TYPE job_source ADD VALUE IF NOT EXISTS 'GREENHOUSE';
ALTER TYPE job_source ADD VALUE IF NOT EXISTS 'LEVER';
