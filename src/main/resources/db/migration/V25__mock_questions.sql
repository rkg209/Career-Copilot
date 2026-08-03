-- Mock-interview question bank (Phase 7): generated per user from own interview logs, the
-- published company-intel pool, and curated role-pattern seeds.
CREATE TYPE mock_question_source AS ENUM ('OWN_LOG', 'PUBLIC_INTEL', 'ROLE_PATTERN');

CREATE TABLE mock_questions (
    id            BIGSERIAL             PRIMARY KEY,
    user_id       UUID                  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company       TEXT,
    role_category TEXT,
    round_type    interview_round_type,
    question_text TEXT                  NOT NULL,
    source        mock_question_source  NOT NULL,
    source_ref_id BIGINT,
    created_at    TIMESTAMPTZ           NOT NULL DEFAULT now()
);

CREATE INDEX idx_mock_questions_user ON mock_questions (user_id);
CREATE INDEX idx_mock_questions_user_company ON mock_questions (user_id, company);
