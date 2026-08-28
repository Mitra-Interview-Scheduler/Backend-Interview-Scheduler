-- V87 was applied before family_id existed; Flyway repair only updated the checksum.
ALTER TABLE user_refresh_tokens
    ADD COLUMN IF NOT EXISTS family_id UUID;

UPDATE user_refresh_tokens
SET family_id = gen_random_uuid()
WHERE family_id IS NULL;

ALTER TABLE user_refresh_tokens
    ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_refresh_tokens_family_id
    ON user_refresh_tokens (family_id);
