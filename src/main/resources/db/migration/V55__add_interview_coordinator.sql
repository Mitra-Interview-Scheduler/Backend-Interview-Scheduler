ALTER TABLE interview_requests
  ADD COLUMN interview_coordinator_id BIGINT REFERENCES users(id);

ALTER TABLE interview_panels
  ADD COLUMN interview_coordinator_id BIGINT REFERENCES users(id);

CREATE INDEX idx_interview_requests_coordinator ON interview_requests (interview_coordinator_id);
CREATE INDEX idx_interview_panels_coordinator ON interview_panels (interview_coordinator_id);
