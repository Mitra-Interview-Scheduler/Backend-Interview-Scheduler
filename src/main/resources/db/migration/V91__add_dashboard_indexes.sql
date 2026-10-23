-- Indexes for the role-based dashboard queries. PostgreSQL does not index foreign keys
-- automatically, and the dashboards filter/sort by these columns on every load.

-- interview_schedules: joins to the request, per-interviewer windows, upcoming ranges, overdue feedback
CREATE INDEX IF NOT EXISTS idx_interview_schedules_request
    ON interview_schedules (request_id);
CREATE INDEX IF NOT EXISTS idx_interview_schedules_interviewer_start
    ON interview_schedules (interviewer_id, start_date_time);
CREATE INDEX IF NOT EXISTS idx_interview_schedules_start_active
    ON interview_schedules (start_date_time) WHERE status <> 'CANCELLED';
CREATE INDEX IF NOT EXISTS idx_interview_schedules_feedback_overdue
    ON interview_schedules (status, end_date_time) WHERE assessment_phase IS NULL;

-- feedback_responses: turnaround and per-interviewer history
CREATE INDEX IF NOT EXISTS idx_feedback_responses_interviewer_submitted
    ON feedback_responses (interviewer_id, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_feedback_responses_submitted
    ON feedback_responses (submitted_at);

-- candidate_pipeline_status_events: trend windows and latest-change lookups
CREATE INDEX IF NOT EXISTS idx_pipeline_status_events_created
    ON candidate_pipeline_status_events (created_at);
CREATE INDEX IF NOT EXISTS idx_pipeline_status_events_candidate_created
    ON candidate_pipeline_status_events (candidate_id, created_at DESC);

-- notifications: daily activity counts
CREATE INDEX IF NOT EXISTS idx_notifications_created
    ON notifications (created_at);

-- interview_postpone_requests: per-requester history and trend counts
CREATE INDEX IF NOT EXISTS idx_postpone_requested_by_created
    ON interview_postpone_requests (requested_by_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_postpone_created
    ON interview_postpone_requests (created_at);
