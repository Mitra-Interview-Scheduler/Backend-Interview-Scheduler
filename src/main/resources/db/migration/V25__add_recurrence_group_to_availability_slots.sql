ALTER TABLE availability_slots
ADD COLUMN IF NOT EXISTS recurrence_group_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_availability_recurrence_group
ON availability_slots (interviewer_id, recurrence_group_id, start_date_time)
WHERE is_active = true;
