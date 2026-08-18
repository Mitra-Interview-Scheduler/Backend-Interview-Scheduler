CREATE TABLE IF NOT EXISTS user_refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP NULL,
    created_by_ip VARCHAR(128),
    user_agent VARCHAR(512),
    replaced_by_token_hash VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_user_refresh_tokens_user_id
    ON user_refresh_tokens(user_id);

CREATE INDEX IF NOT EXISTS idx_user_refresh_tokens_expires_at
    ON user_refresh_tokens(expires_at);
