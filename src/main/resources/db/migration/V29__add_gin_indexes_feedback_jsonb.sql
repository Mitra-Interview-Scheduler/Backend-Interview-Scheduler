-- Add GIN indexes for jsonb columns used in querying

CREATE INDEX IF NOT EXISTS idx_feedback_forms_department_ids_gin ON feedback_forms USING gin (department_ids_json);
CREATE INDEX IF NOT EXISTS idx_feedback_forms_designation_ids_gin ON feedback_forms USING gin (designation_ids_json);
CREATE INDEX IF NOT EXISTS idx_feedback_questions_options_gin ON feedback_questions USING gin (options_json);
CREATE INDEX IF NOT EXISTS idx_feedback_responses_responses_gin ON feedback_responses USING gin (responses_json);
