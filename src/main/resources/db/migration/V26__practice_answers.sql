-- Typed answers to mock questions, graded 0-5 by an LLM rubric (Phase 7).
CREATE TABLE practice_answers (
    id               BIGSERIAL   PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mock_question_id BIGINT      NOT NULL REFERENCES mock_questions (id) ON DELETE CASCADE,
    answer_text      TEXT        NOT NULL,
    quality_grade    INTEGER     CHECK (quality_grade BETWEEN 0 AND 5),
    feedback         TEXT,
    llm_model        TEXT,
    token_count      INTEGER     CHECK (token_count >= 0),
    answered_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practice_answers_user ON practice_answers (user_id);
CREATE INDEX idx_practice_answers_question ON practice_answers (mock_question_id);
