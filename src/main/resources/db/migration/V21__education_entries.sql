-- Education history (Phase 2E): structured facts, not embedded — injected deterministically
-- into resume JSON after the LLM call.
CREATE TYPE education_score_type AS ENUM ('CPI', 'PERCENTAGE', 'GRADE');

CREATE TABLE education_entries (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    degree         TEXT         NOT NULL,
    institution    TEXT         NOT NULL,
    university     TEXT,
    specialization TEXT,
    start_year     INTEGER,
    end_year       INTEGER,
    score_type     education_score_type,
    score_value    TEXT,
    display_order  INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_education_entries_user ON education_entries (user_id, display_order);
