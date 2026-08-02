-- Assessment review workflow: phase + file on schedules, multi-reviewer assignments

ALTER TABLE interview_schedules
    ADD COLUMN IF NOT EXISTS assessment_phase VARCHAR(32),
    ADD COLUMN IF NOT EXISTS assessment_file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS assessment_content_type VARCHAR(150),
    ADD COLUMN IF NOT EXISTS assessment_file_size BIGINT,
    ADD COLUMN IF NOT EXISTS assessment_file_data BYTEA,
    ADD COLUMN IF NOT EXISTS assessment_uploaded_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS assessment_reviewers (
    id BIGSERIAL PRIMARY KEY,
    interview_schedule_id BIGINT NOT NULL REFERENCES interview_schedules(id) ON DELETE CASCADE,
    reviewer_user_id BIGINT NOT NULL REFERENCES users(id),
    assigned_by_user_id BIGINT REFERENCES users(id),
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_assessment_reviewer UNIQUE (interview_schedule_id, reviewer_user_id)
);

CREATE INDEX IF NOT EXISTS idx_assessment_reviewers_schedule
    ON assessment_reviewers (interview_schedule_id);

CREATE INDEX IF NOT EXISTS idx_assessment_reviewers_reviewer
    ON assessment_reviewers (reviewer_user_id);

CREATE INDEX IF NOT EXISTS idx_interview_schedules_assessment_phase
    ON interview_schedules (assessment_phase)
    WHERE assessment_phase IS NOT NULL;
