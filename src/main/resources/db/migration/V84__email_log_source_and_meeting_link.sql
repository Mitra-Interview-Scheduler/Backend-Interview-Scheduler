-- Track email origin and optional meeting link on delivery logs.
ALTER TABLE email_delivery_logs
    ADD COLUMN IF NOT EXISTS source VARCHAR(64) NOT NULL DEFAULT 'SYSTEM';

ALTER TABLE email_delivery_logs
    ADD COLUMN IF NOT EXISTS meeting_link TEXT;

-- Drop temporary default after backfill for existing rows.
ALTER TABLE email_delivery_logs
    ALTER COLUMN source DROP DEFAULT;
