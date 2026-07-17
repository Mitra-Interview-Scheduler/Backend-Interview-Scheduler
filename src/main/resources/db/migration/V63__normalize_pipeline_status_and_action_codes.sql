-- Store compact status/action codes instead of full enum names.

ALTER TABLE candidate_pipeline_steps
    DROP CONSTRAINT IF EXISTS candidate_pipeline_steps_step_status_check;

UPDATE candidate_pipeline_steps
SET step_status = CASE step_status
    WHEN 'PENDING' THEN 'PND'
    WHEN 'CURRENT' THEN 'CUR'
    WHEN 'COMPLETED' THEN 'CMP'
    WHEN 'FAILED' THEN 'FAL'
    WHEN 'SKIPPED' THEN 'SKP'
    ELSE step_status
END
WHERE step_status IN ('PENDING', 'CURRENT', 'COMPLETED', 'FAILED', 'SKIPPED');

ALTER TABLE candidate_pipeline_steps
    ADD CONSTRAINT candidate_pipeline_steps_step_status_check
        CHECK (step_status IN ('PND', 'CUR', 'CMP', 'FAL', 'SKP'));

UPDATE candidate_pipeline_status_events
SET action_type = CASE action_type
    WHEN 'STATUS_CHANGED' THEN 'STC'
    WHEN 'SCREENING_SAVED' THEN 'SCS'
    WHEN 'APPLICATION_CLOSED' THEN 'ACL'
    WHEN 'INTERVIEW_SCHEDULED' THEN 'IVS'
    WHEN 'INTERVIEW_CANCELLED' THEN 'IVC'
    ELSE action_type
END
WHERE action_type IN (
    'STATUS_CHANGED',
    'SCREENING_SAVED',
    'APPLICATION_CLOSED',
    'INTERVIEW_SCHEDULED',
    'INTERVIEW_CANCELLED'
);
