CREATE TABLE interview_postpone_requests (
    id BIGSERIAL PRIMARY KEY,
    interview_schedule_id BIGINT NOT NULL REFERENCES interview_schedules(id),
    interview_request_id BIGINT NOT NULL REFERENCES interview_requests(id),
    requested_by_id BIGINT NOT NULL REFERENCES users(id),
    reason VARCHAR(2000) NOT NULL,
    preferred_start_date_time TIMESTAMP,
    preferred_end_date_time TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewed_by_id BIGINT REFERENCES users(id),
    review_notes VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP
);

CREATE INDEX idx_postpone_schedule_status ON interview_postpone_requests (interview_schedule_id, status);
CREATE INDEX idx_postpone_status_created ON interview_postpone_requests (status, created_at DESC);

CREATE UNIQUE INDEX idx_postpone_one_pending_per_schedule
    ON interview_postpone_requests (interview_schedule_id)
    WHERE status = 'PENDING';
