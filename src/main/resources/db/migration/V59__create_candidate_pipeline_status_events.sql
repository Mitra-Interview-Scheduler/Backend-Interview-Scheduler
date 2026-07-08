CREATE TABLE IF NOT EXISTS candidate_pipeline_status_events (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL REFERENCES candidates (id) ON DELETE CASCADE,
    status_key VARCHAR(50) NOT NULL,
    previous_status_key VARCHAR(50),
    action_type VARCHAR(50) NOT NULL,
    changed_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    changed_by_name VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pipeline_status_events_candidate
    ON candidate_pipeline_status_events (candidate_id, created_at DESC);
