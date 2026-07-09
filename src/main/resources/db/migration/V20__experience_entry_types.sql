-- Generalize experience_entries into a typed evidence corpus (Phase 2E).
-- Existing rows stay WORK; the embedding/retrieval pipeline is shared across all types.
CREATE TYPE corpus_entry_type AS ENUM
    ('WORK', 'INTERNSHIP', 'PROJECT', 'POSITION_OF_RESPONSIBILITY', 'PUBLICATION');

CREATE TYPE project_type AS ENUM ('COURSE', 'SELF', 'RESEARCH', 'THESIS');

ALTER TABLE experience_entries
    ADD COLUMN entry_type   corpus_entry_type NOT NULL DEFAULT 'WORK',
    ADD COLUMN project_type project_type,
    ADD COLUMN guide        TEXT,
    ADD COLUMN context      TEXT,
    ADD COLUMN url          TEXT;

CREATE INDEX idx_experience_entries_user_type ON experience_entries (user_id, entry_type);
