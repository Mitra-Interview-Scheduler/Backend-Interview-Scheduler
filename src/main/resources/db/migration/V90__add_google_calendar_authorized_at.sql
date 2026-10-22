ALTER TABLE user_google_calendar_credentials
    ADD COLUMN IF NOT EXISTS authorized_at TIMESTAMP;

UPDATE user_google_calendar_credentials
SET authorized_at = COALESCE(connected_at, updated_at, NOW())
WHERE authorized_at IS NULL;

ALTER TABLE user_google_calendar_credentials
    ALTER COLUMN authorized_at SET DEFAULT NOW();

ALTER TABLE user_google_calendar_credentials
    ALTER COLUMN authorized_at SET NOT NULL;
