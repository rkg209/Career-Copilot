CREATE TYPE interview_round_type AS ENUM (
    'PHONE_SCREEN','TECHNICAL','SYSTEM_DESIGN','BEHAVIOURAL','TAKE_HOME','PANEL','HR','OTHER'
);
CREATE TYPE contribution_status AS ENUM ('STAGED','PUBLISHED');

CREATE TABLE interview_logs (
    id                  BIGSERIAL            PRIMARY KEY,
    application_id      BIGINT               NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    user_id             UUID                 NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    round_type          interview_round_type NOT NULL,
    round_number        SMALLINT             NOT NULL DEFAULT 1 CHECK (round_number >= 1),
    interview_date      DATE,
    duration_minutes    SMALLINT             CHECK (duration_minutes > 0),
    interviewer_role    TEXT,
    questions_asked     TEXT[]               NOT NULL DEFAULT '{}',
    notes               TEXT,
    feedback_received   TEXT,
    outcome             TEXT,
    difficulty          SMALLINT             CHECK (difficulty BETWEEN 1 AND 5),
    contributed_at      TIMESTAMPTZ,
    contribution_status contribution_status,
    created_at          TIMESTAMPTZ          NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ          NOT NULL DEFAULT now()
);
CREATE INDEX idx_interview_logs_application_id ON interview_logs (application_id);
CREATE INDEX idx_interview_logs_user_id        ON interview_logs (user_id);
CREATE INDEX idx_interview_logs_contributed_at ON interview_logs (contributed_at) WHERE contributed_at IS NULL;
