-- Convert existing TEXT JSON columns to jsonb for better querying and indexing

BEGIN;

-- Convert feedback_forms JSON text columns
ALTER TABLE feedback_forms
    ALTER COLUMN department_ids_json DROP DEFAULT,
    ALTER COLUMN designation_ids_json DROP DEFAULT;

ALTER TABLE feedback_forms
    ALTER COLUMN department_ids_json TYPE jsonb USING department_ids_json::jsonb,
    ALTER COLUMN designation_ids_json TYPE jsonb USING designation_ids_json::jsonb;

-- Convert feedback_questions options_json
ALTER TABLE feedback_questions
    ALTER COLUMN options_json DROP DEFAULT;

ALTER TABLE feedback_questions
    ALTER COLUMN options_json TYPE jsonb USING options_json::jsonb;

-- Convert feedback_responses responses_json
ALTER TABLE feedback_responses
    ALTER COLUMN responses_json DROP DEFAULT;

ALTER TABLE feedback_responses
    ALTER COLUMN responses_json TYPE jsonb USING responses_json::jsonb;

-- Set sensible defaults
ALTER TABLE feedback_forms
    ALTER COLUMN department_ids_json SET DEFAULT '[]'::jsonb,
    ALTER COLUMN designation_ids_json SET DEFAULT '[]'::jsonb;

ALTER TABLE feedback_questions
    ALTER COLUMN options_json SET DEFAULT '[]'::jsonb;

ALTER TABLE feedback_responses
    ALTER COLUMN responses_json SET DEFAULT '{}'::jsonb;

COMMIT;
