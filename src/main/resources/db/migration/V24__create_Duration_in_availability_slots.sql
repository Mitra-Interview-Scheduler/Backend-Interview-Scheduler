ALTER TABLE availability_slots
ADD COLUMN IF NOT EXISTS duration_hours NUMERIC(4, 2) GENERATED ALWAYS AS (EXTRACT(EPOCH FROM (end_date_time - start_date_time)) / 3600.0) STORED;