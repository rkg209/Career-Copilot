-- SM-2 spaced-repetition state, one card per user+question (Phase 7).
CREATE TABLE review_cards (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mock_question_id BIGINT       NOT NULL REFERENCES mock_questions (id) ON DELETE CASCADE,
    ease_factor      NUMERIC(4,2) NOT NULL DEFAULT 2.50,
    interval_days    INTEGER      NOT NULL DEFAULT 0,
    repetitions      INTEGER      NOT NULL DEFAULT 0,
    last_grade       INTEGER      CHECK (last_grade BETWEEN 0 AND 5),
    due_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_review_cards_user_question UNIQUE (user_id, mock_question_id)
);

CREATE INDEX idx_review_cards_due ON review_cards (user_id, due_at);
