ALTER TABLE interview_schedules
    ADD COLUMN IF NOT EXISTS interview_type VARCHAR(20);

UPDATE interview_schedules
SET interview_type = 'TECHNICAL'
WHERE interview_type IS NULL;
