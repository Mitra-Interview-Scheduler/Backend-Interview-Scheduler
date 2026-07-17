ALTER TABLE feedback_forms
    ADD COLUMN IF NOT EXISTS interview_types_json jsonb NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_feedback_forms_interview_types_gin
    ON feedback_forms USING gin (interview_types_json);
