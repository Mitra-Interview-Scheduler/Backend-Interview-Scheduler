-- Rename candidate master step SCHEDULED to INTERVIEW_SCHEDULES to distinguish
-- it from interview_schedules.status (InterviewStatus.SCHEDULED).
--
-- V41 already replaced candidate_pipeline_steps.status_key with master_step_id,
-- and V42 did the same for candidates. On fresh DBs V36 already seeds an
-- INTERVIEW_SCHEDULES row, so a plain rename of SCHEDULED collides with the
-- unique constraint on status_key. Handle both cases idempotently:
--   * If INTERVIEW_SCHEDULES already exists, repoint FK child rows off the
--     legacy SCHEDULED master_steps.id, then delete that row.
--   * Otherwise, rename SCHEDULED -> INTERVIEW_SCHEDULES.

DO $$
DECLARE
    scheduled_id BIGINT;
    interview_schedules_id BIGINT;
BEGIN
    SELECT id INTO scheduled_id
    FROM master_steps
    WHERE status_key = 'SCHEDULED';

    SELECT id INTO interview_schedules_id
    FROM master_steps
    WHERE status_key = 'INTERVIEW_SCHEDULES';

    IF interview_schedules_id IS NOT NULL THEN
        IF scheduled_id IS NOT NULL THEN
            IF EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'candidate_pipeline_steps'
                  AND column_name = 'master_step_id'
            ) THEN
                UPDATE candidate_pipeline_steps
                SET master_step_id = interview_schedules_id
                WHERE master_step_id = scheduled_id;
            END IF;

            IF EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'candidates'
                  AND column_name = 'master_step_id'
            ) THEN
                UPDATE candidates
                SET master_step_id = interview_schedules_id
                WHERE master_step_id = scheduled_id;
            END IF;

            DELETE FROM master_steps WHERE id = scheduled_id;
        END IF;
    ELSE
        UPDATE master_steps
        SET status_key = 'INTERVIEW_SCHEDULES',
            label = 'Interview Schedules'
        WHERE status_key = 'SCHEDULED';
    END IF;
END$$;
