-- V47__create_candidate_closing_events.sql

CREATE TABLE IF NOT EXISTS candidate_closing_events (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL REFERENCES candidates(id),
    closing_status VARCHAR(50) NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_candidate_closing_events_candidate_id
    ON candidate_closing_events(candidate_id);

CREATE INDEX IF NOT EXISTS idx_candidate_closing_events_created_at
    ON candidate_closing_events(candidate_id, created_at DESC);
