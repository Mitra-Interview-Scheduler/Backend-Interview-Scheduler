-- Durable audit of HR feedback nudges (survives notification retention purge)

CREATE TABLE IF NOT EXISTS feedback_nudge_logs (
    id BIGSERIAL PRIMARY KEY,
    interview_schedule_id BIGINT NOT NULL REFERENCES interview_schedules(id) ON DELETE CASCADE,
    nudged_by_user_id BIGINT NOT NULL REFERENCES users(id),
    recipient_user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_feedback_nudge_logs_schedule
    ON feedback_nudge_logs (interview_schedule_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_feedback_nudge_logs_nudged_by
    ON feedback_nudge_logs (nudged_by_user_id, created_at DESC);
