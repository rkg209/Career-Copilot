-- Roles this posting matched: the aggregator query role, and/or ATS keyword-matched roles.
-- Empty array = matched no configured role. Filtered via array containment (matched_roles @> ARRAY[role]).
ALTER TABLE job_postings
    ADD COLUMN IF NOT EXISTS matched_roles TEXT[] NOT NULL DEFAULT '{}';

-- GIN index for containment lookups used by role-filtered browse/search.
CREATE INDEX IF NOT EXISTS idx_job_postings_matched_roles
    ON job_postings USING GIN (matched_roles);
