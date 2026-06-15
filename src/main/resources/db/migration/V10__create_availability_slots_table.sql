-- V10__create_availability_slots_table.sql
-- V1 created availability_slots with a legacy day_of_week/time schema.
-- Drop and recreate so the entity-matching datetime schema is applied on fresh installs.

DROP TABLE IF EXISTS availability_slots CASCADE;

CREATE TABLE availability_slots (
    id BIGSERIAL PRIMARY KEY,
    interviewer_id BIGINT NOT NULL,
    start_date_time TIMESTAMP NOT NULL,
    end_date_time TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    description VARCHAR(500),
    interview_schedule_id BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_availability_interviewer FOREIGN KEY (interviewer_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_availability_schedule FOREIGN KEY (interview_schedule_id)
        REFERENCES interview_schedules(id) ON DELETE SET NULL,
    CONSTRAINT chk_end_after_start CHECK (end_date_time > start_date_time)
);

-- Create indexes for efficient querying
CREATE INDEX idx_availability_interviewer ON availability_slots (interviewer_id, is_active);
CREATE INDEX idx_availability_datetime ON availability_slots (start_date_time, end_date_time);
CREATE INDEX idx_availability_status ON availability_slots (status, is_active);
CREATE INDEX idx_availability_interviewer_datetime
    ON availability_slots (interviewer_id, start_date_time, end_date_time)
    WHERE is_active = true;

-- Index for finding available slots
CREATE INDEX idx_availability_available_slots
    ON availability_slots (interviewer_id, start_date_time)
    WHERE status = 'AVAILABLE' AND is_active = true;