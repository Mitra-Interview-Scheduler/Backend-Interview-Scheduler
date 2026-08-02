ALTER TABLE interview_types
  ADD COLUMN IF NOT EXISTS create_calendar_meeting BOOLEAN NOT NULL DEFAULT TRUE;
