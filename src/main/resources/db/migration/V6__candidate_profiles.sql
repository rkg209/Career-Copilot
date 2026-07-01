CREATE TABLE candidate_profiles (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             UUID            NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    headline            TEXT,
    summary             TEXT,
    location            TEXT,
    linkedin_url        TEXT,
    github_url          TEXT,
    portfolio_url       TEXT,
    target_roles        TEXT[]          NOT NULL DEFAULT '{}',
    target_locations    TEXT[]          NOT NULL DEFAULT '{}',
    open_to_remote      BOOLEAN         NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_candidate_profiles_user_id UNIQUE (user_id)
);
