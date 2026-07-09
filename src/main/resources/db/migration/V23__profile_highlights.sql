-- Year-tagged bullet sections (Phase 2E): scholastic achievements, certifications,
-- extracurriculars. Structured facts, not embedded.
CREATE TYPE highlight_type AS ENUM
    ('SCHOLASTIC_ACHIEVEMENT', 'CERTIFICATION', 'EXTRACURRICULAR');

CREATE TABLE profile_highlights (
    id            BIGSERIAL      PRIMARY KEY,
    user_id       UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type          highlight_type NOT NULL,
    text          TEXT           NOT NULL,
    year_label    TEXT,
    display_order INTEGER        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_profile_highlights_user ON profile_highlights (user_id, type, display_order);
