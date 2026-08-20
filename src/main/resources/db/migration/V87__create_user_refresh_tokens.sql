CREATE TABLE user_refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    family_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_user_refresh_tokens_user_id ON user_refresh_tokens (user_id);
CREATE INDEX idx_user_refresh_tokens_family_id ON user_refresh_tokens (family_id);
