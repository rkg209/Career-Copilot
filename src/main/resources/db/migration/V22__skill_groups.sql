-- Categorized technical skills (Phase 2E), e.g. "Programming Languages: C++, Python".
CREATE TABLE skill_groups (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          TEXT         NOT NULL,
    skills        TEXT[]       NOT NULL DEFAULT '{}',
    display_order INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_skill_groups_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_skill_groups_user ON skill_groups (user_id, display_order);
