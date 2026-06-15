-- V36__fix_schema_and_reseed_master_steps.sql
-- Repairs databases that were affected by ddl-auto=create wiping Flyway seed data,
-- or that still have legacy schema/status-key mismatches.

-- Recreate master_steps if ddl-auto=create dropped it after Flyway ran
CREATE TABLE IF NOT EXISTS master_steps (
    id BIGSERIAL PRIMARY KEY,
    status_key VARCHAR(50) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    step_order INTEGER NOT NULL,
    display_order INTEGER NOT NULL,
    bg_color VARCHAR(20) NOT NULL,
    badge_class VARCHAR(255) NOT NULL,
    light_class VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_closing_step BOOLEAN NOT NULL DEFAULT FALSE,
    is_default_step BOOLEAN NOT NULL DEFAULT FALSE,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_master_steps_active_order
    ON master_steps (is_active, step_order, display_order);

-- Recreate candidate_pipeline_steps if missing
CREATE TABLE IF NOT EXISTS candidate_pipeline_steps (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    status_key VARCHAR(50) NOT NULL,
    sequence_order INTEGER NOT NULL,
    step_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    custom_label VARCHAR(100),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pipeline_steps_candidate
        FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_steps_master_step
        FOREIGN KEY (status_key) REFERENCES master_steps (status_key),
    CONSTRAINT uk_candidate_sequence_order
        UNIQUE (candidate_id, sequence_order)
);

CREATE INDEX IF NOT EXISTS idx_candidate_pipeline_lookup
    ON candidate_pipeline_steps (candidate_id, sequence_order);

-- Align master_steps status key with MasterStatus.SCHEDULED enum value
UPDATE master_steps
SET status_key = 'SCHEDULED',
    label = 'Scheduled'
WHERE status_key = 'INTERVIEW_SESSION';

UPDATE candidate_pipeline_steps
SET status_key = 'SCHEDULED'
WHERE status_key = 'INTERVIEW_SESSION';

-- Rename legacy notifications.is_read column if V1 schema was never replaced
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'is_read'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'read'
    ) THEN
        ALTER TABLE notifications RENAME COLUMN is_read TO "read";
    END IF;
END$$;

-- Replace legacy availability_slots schema (V1 day_of_week/time columns)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'availability_slots'
          AND column_name = 'day_of_week'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'availability_slots'
          AND column_name = 'start_date_time'
    ) THEN
        DROP TABLE availability_slots CASCADE;

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

        CREATE INDEX idx_availability_interviewer ON availability_slots (interviewer_id, is_active);
        CREATE INDEX idx_availability_datetime ON availability_slots (start_date_time, end_date_time);
        CREATE INDEX idx_availability_status ON availability_slots (status, is_active);
    END IF;
END$$;

-- Re-seed master_steps when empty (e.g. after Hibernate ddl-auto=create wiped Flyway data)
INSERT INTO master_steps (
    status_key, label, step_order, display_order, bg_color, badge_class, light_class,
    is_active, is_closing_step, is_default_step, is_visible
) VALUES
    ('NEW', 'New', 1, 10, '#3b82f6', 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200', 'bg-blue-100', TRUE, FALSE, TRUE, TRUE),
    ('SCREENING', 'Screening', 2, 20, '#eab308', 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200', 'bg-yellow-100', TRUE, FALSE, TRUE, TRUE),
    ('INTERVIEW_SESSION', 'Interview Session', 3, 30, '#a855f7', 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200', 'bg-purple-100', TRUE, FALSE, TRUE, FALSE),
    ('TECHNICAL_ROUND', 'Technical', 3, 40, '#06b6d4', 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-200', 'bg-cyan-100', TRUE, FALSE, FALSE, TRUE),
    ('HR_ROUND', 'HR Round', 3, 50, '#ec4899', 'bg-pink-100 text-pink-800 dark:bg-pink-900 dark:text-pink-200', 'bg-pink-100', TRUE, FALSE, FALSE, TRUE),
    ('DISPOSITION', 'Disposition', 4, 60, '#6b7280', 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200', 'bg-gray-100', TRUE, TRUE, TRUE, FALSE),
    ('SELECTED', 'Selected', 4, 70, '#22c55e', 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200', 'bg-green-100', TRUE, TRUE, FALSE, TRUE),
    ('REJECTED', 'Rejected', 4, 80, '#ef4444', 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200', 'bg-red-100', TRUE, TRUE, FALSE, TRUE),
    ('ON_HOLD', 'On Hold', 4, 90, '#f97316', 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200', 'bg-orange-100', TRUE, TRUE, FALSE, TRUE),
    ('WITHDRAWN', 'Withdrawn', 4, 100, '#6b7280', 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200', 'bg-gray-100', TRUE, TRUE, FALSE, TRUE)
ON CONFLICT (status_key) DO UPDATE SET
    label = EXCLUDED.label,
    step_order = EXCLUDED.step_order,
    display_order = EXCLUDED.display_order,
    bg_color = EXCLUDED.bg_color,
    badge_class = EXCLUDED.badge_class,
    light_class = EXCLUDED.light_class,
    is_active = EXCLUDED.is_active,
    is_closing_step = EXCLUDED.is_closing_step,
    is_default_step = EXCLUDED.is_default_step,
    is_visible = EXCLUDED.is_visible;
