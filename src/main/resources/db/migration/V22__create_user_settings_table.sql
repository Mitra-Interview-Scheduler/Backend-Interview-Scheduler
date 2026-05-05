CREATE TABLE user_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    preferred_date_format VARCHAR(64) NOT NULL DEFAULT 'yyyy-MM-dd',
    preferred_time_format VARCHAR(64) NOT NULL DEFAULT 'HH:mm',
    timezone_captured BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO user_settings (user_id, timezone, preferred_date_format, preferred_time_format, timezone_captured)
SELECT id, 'UTC', 'yyyy-MM-dd', 'HH:mm', FALSE
FROM users;


