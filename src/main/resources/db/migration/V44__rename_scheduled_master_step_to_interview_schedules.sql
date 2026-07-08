-- Rename candidate master step SCHEDULED to INTERVIEW_SCHEDULES to distinguish
-- it from interview_schedules.status (InterviewStatus.SCHEDULED).
--
-- On fresh databases V36 already seeds an INTERVIEW_SCHEDULES row, so a plain
-- rename of SCHEDULED collides with the unique constraint on status_key. Handle
-- both cases idempotently:
--   * If INTERVIEW_SCHEDULES already exists, repoint any references off the
--     legacy SCHEDULED row and delete it.
--   * Otherwise, rename SCHEDULED -> INTERVIEW_SCHEDULES.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM master_steps WHERE status_key = 'INTERVIEW_SCHEDULES') THEN
        -- Move any child rows off the legacy SCHEDULED key before removing it.
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = 'candidate_pipeline_steps'
        ) THEN
            UPDATE candidate_pipeline_steps
            SET status_key = 'INTERVIEW_SCHEDULES'
            WHERE status_key = 'SCHEDULED';
        END IF;

        DELETE FROM master_steps WHERE status_key = 'SCHEDULED';
    ELSE
        UPDATE master_steps
        SET status_key = 'INTERVIEW_SCHEDULES',
            label = 'Interview Schedules'
        WHERE status_key = 'SCHEDULED';
    END IF;
END$$;
