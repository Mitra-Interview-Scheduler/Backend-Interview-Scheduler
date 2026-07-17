-- V20__alter_userTable_for_googleAuth.sql

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(20);

UPDATE users
SET auth_provider = 'LOCAL'
WHERE auth_provider IS NULL;

ALTER TABLE users
    ALTER COLUMN auth_provider SET DEFAULT 'LOCAL',
    ALTER COLUMN auth_provider SET NOT NULL;

ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS check_password_or_provider;

ALTER TABLE users
    ADD CONSTRAINT check_password_or_provider
        CHECK (
            (auth_provider = 'LOCAL' AND password_hash IS NOT NULL)
            OR
            (auth_provider <> 'LOCAL')
        );
