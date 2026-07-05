-- Supports the default browse ordering (is_active + posted_at DESC) and the deactivate-stale sweep.
CREATE INDEX IF NOT EXISTS idx_job_postings_active_posted_at
    ON job_postings (is_active, posted_at DESC);
