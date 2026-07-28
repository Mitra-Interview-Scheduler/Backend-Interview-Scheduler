ALTER TABLE user_google_calendar_credentials
    ADD COLUMN IF NOT EXISTS selected_calendar_ids TEXT;
