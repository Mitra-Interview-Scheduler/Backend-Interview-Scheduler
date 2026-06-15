-- V14__add_candidate_reference_and_update_notifications.sql

-- Add candidate_id to interview_requests table
ALTER TABLE interview_requests
ADD COLUMN IF NOT EXISTS candidate_id BIGINT,
ADD CONSTRAINT fk_interview_request_candidate
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_interview_requests_candidate
    ON interview_requests(candidate_id);

-- Update notifications table with new fields
ALTER TABLE notifications
ADD COLUMN IF NOT EXISTS related_entity_id BIGINT,
ADD COLUMN IF NOT EXISTS related_entity_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS read_at TIMESTAMP;

-- Create index for faster notification queries
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read
    ON notifications(recipient_id, "read");

CREATE INDEX IF NOT EXISTS idx_notifications_related_entity
    ON notifications(related_entity_type, related_entity_id);

-- Update existing PENDING requests to ACCEPTED (migration for existing data)
UPDATE interview_requests
SET status = 'ACCEPTED',
    responded_at = COALESCE(responded_at, created_at),
    response_notes = COALESCE(response_notes, 'Auto-accepted during system migration')
WHERE status = 'PENDING';

COMMENT ON COLUMN interview_requests.candidate_id IS 'Reference to candidate record if scheduling from candidates list';
COMMENT ON COLUMN notifications.related_entity_id IS 'ID of related entity (interview request, slot, etc)';
COMMENT ON COLUMN notifications.related_entity_type IS 'Type of related entity';