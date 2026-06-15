-- V11__create_interview_requests_table.sql
-- V1 created interview_requests with a legacy preferred_date/time schema.
-- Drop and recreate so the entity-matching datetime schema is applied on fresh installs.

DROP TABLE IF EXISTS interview_request_technologies CASCADE;
DROP TABLE IF EXISTS interview_requests_technologies CASCADE;
DROP TABLE IF EXISTS interview_requests CASCADE;

CREATE TABLE interview_requests (
    id BIGSERIAL PRIMARY KEY,
    candidate_name VARCHAR(255) NOT NULL,
    candidate_designation_id BIGINT,
    preferred_start_date_time TIMESTAMP NOT NULL,
    preferred_end_date_time TIMESTAMP NOT NULL,
    requested_by_id BIGINT NOT NULL,
    assigned_interviewer_id BIGINT,
    availability_slot_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_urgent BOOLEAN NOT NULL DEFAULT false,
    notes TEXT,
    response_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP
);

-- Add foreign keys only if they don't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_request_designation'
    ) THEN
        ALTER TABLE interview_requests
        ADD CONSTRAINT fk_request_designation FOREIGN KEY (candidate_designation_id)
            REFERENCES designations(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_request_requested_by'
    ) THEN
        ALTER TABLE interview_requests
        ADD CONSTRAINT fk_request_requested_by FOREIGN KEY (requested_by_id)
            REFERENCES users(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_request_interviewer'
    ) THEN
        ALTER TABLE interview_requests
        ADD CONSTRAINT fk_request_interviewer FOREIGN KEY (assigned_interviewer_id)
            REFERENCES users(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_request_availability_slot'
    ) THEN
        ALTER TABLE interview_requests
        ADD CONSTRAINT fk_request_availability_slot FOREIGN KEY (availability_slot_id)
            REFERENCES availability_slots(id) ON DELETE SET NULL;
    END IF;
END$$;

CREATE TABLE interview_requests_technologies (
    interview_request_id BIGINT NOT NULL,
    technology_id BIGINT NOT NULL,
    PRIMARY KEY (interview_request_id, technology_id)
);

-- Add foreign keys for junction table
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_request_tech_request'
    ) THEN
        ALTER TABLE interview_requests_technologies
        ADD CONSTRAINT fk_request_tech_request FOREIGN KEY (interview_request_id)
            REFERENCES interview_requests(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_request_tech_technology'
    ) THEN
        ALTER TABLE interview_requests_technologies
        ADD CONSTRAINT fk_request_tech_technology FOREIGN KEY (technology_id)
            REFERENCES technologies(id) ON DELETE CASCADE;
    END IF;
END$$;

-- Create indexes only if they don't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_interview_requests_interviewer') THEN
        CREATE INDEX idx_interview_requests_interviewer ON interview_requests(assigned_interviewer_id, status);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_interview_requests_requested_by') THEN
        CREATE INDEX idx_interview_requests_requested_by ON interview_requests(requested_by_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_interview_requests_status') THEN
        CREATE INDEX idx_interview_requests_status ON interview_requests(status, created_at);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_interview_requests_urgent') THEN
        CREATE INDEX idx_interview_requests_urgent ON interview_requests(is_urgent, status);
    END IF;
END$$;