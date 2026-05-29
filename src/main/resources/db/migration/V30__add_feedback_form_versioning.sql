BEGIN;

ALTER TABLE feedback_forms
    ADD COLUMN IF NOT EXISTS series_key VARCHAR(36),
    ADD COLUMN IF NOT EXISTS version_number INT NOT NULL DEFAULT 1;

UPDATE feedback_forms
SET series_key = COALESCE(series_key, CONCAT('form-', id::text))
WHERE series_key IS NULL OR series_key = '';

ALTER TABLE feedback_forms
    ALTER COLUMN series_key SET NOT NULL,
    ALTER COLUMN version_number SET DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_feedback_forms_series_key ON feedback_forms(series_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_feedback_forms_series_version ON feedback_forms(series_key, version_number);

COMMIT;