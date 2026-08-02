ALTER TABLE interview_requests ADD COLUMN IF NOT EXISTS candidate_invite_email VARCHAR(255);
ALTER TABLE interview_panels ADD COLUMN IF NOT EXISTS candidate_invite_email VARCHAR(255);
