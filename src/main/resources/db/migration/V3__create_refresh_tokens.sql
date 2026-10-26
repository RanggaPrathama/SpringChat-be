CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_refresh_tokens_session_id ON refresh_tokens(session_id);
CREATE UNIQUE INDEX uk_refresh_tokens_active_session
    ON refresh_tokens(session_id) WHERE consumed_at IS NULL;
