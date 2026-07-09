CREATE TABLE IF NOT EXISTS user_google_calendar_credentials (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    google_account_email VARCHAR(255) NOT NULL,
    encrypted_access_token TEXT NOT NULL,
    encrypted_refresh_token TEXT NOT NULL,
    token_expires_at TIMESTAMP NOT NULL,
    scopes VARCHAR(500) NOT NULL,
    connected_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

ALTER TABLE availability_slots ADD COLUMN IF NOT EXISTS google_calendar_event_id VARCHAR(255);
ALTER TABLE interview_schedules ADD COLUMN IF NOT EXISTS google_calendar_event_id VARCHAR(255);
ALTER TABLE interview_panels ADD COLUMN IF NOT EXISTS google_calendar_event_id VARCHAR(255);
ALTER TABLE interview_panels ADD COLUMN IF NOT EXISTS meeting_link VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_availability_slots_gcal_event ON availability_slots(google_calendar_event_id);
