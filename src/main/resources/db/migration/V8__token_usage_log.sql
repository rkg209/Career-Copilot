CREATE TYPE llm_task AS ENUM ('EXTRACTION','TAILORING','SCORING_AUGMENTATION','COVER_LETTER','EMBEDDING');

CREATE TABLE token_usage_log (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           UUID        REFERENCES users (id) ON DELETE SET NULL,
    task              llm_task    NOT NULL,
    model_name        TEXT        NOT NULL,
    prompt_tokens     INT         NOT NULL CHECK (prompt_tokens >= 0),
    completion_tokens INT         NOT NULL CHECK (completion_tokens >= 0),
    total_tokens      INT         NOT NULL GENERATED ALWAYS AS (prompt_tokens + completion_tokens) STORED,
    latency_ms        INT         CHECK (latency_ms >= 0),
    called_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_token_usage_log_user_id   ON token_usage_log (user_id);
CREATE INDEX idx_token_usage_log_called_at ON token_usage_log (called_at);
