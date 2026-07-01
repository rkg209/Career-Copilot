-- Embedding dimension must match the configured embedding model (nomic-embed-text = 768).
CREATE TABLE experience_entries (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             UUID            NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role                TEXT            NOT NULL,
    company             TEXT            NOT NULL,
    location            TEXT,
    start_date          DATE            NOT NULL,
    end_date            DATE,
    is_current          BOOLEAN         NOT NULL DEFAULT false,
    description         TEXT,
    achievements        TEXT[]          NOT NULL DEFAULT '{}',
    skills_used         TEXT[]          NOT NULL DEFAULT '{}',
    embedding           vector(768),
    text_hash           VARCHAR(64),
    embedded_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_experience_entries_dates   CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_experience_entries_current CHECK (NOT (is_current = true AND end_date IS NOT NULL))
);

CREATE INDEX idx_experience_entries_user_id ON experience_entries (user_id);
CREATE INDEX idx_experience_entries_pending ON experience_entries (id) WHERE embedded_at IS NULL;
CREATE INDEX idx_experience_entries_embedding_hnsw
    ON experience_entries USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
