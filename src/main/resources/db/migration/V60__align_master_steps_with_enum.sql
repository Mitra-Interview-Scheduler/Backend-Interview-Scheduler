-- Align legacy master step keys with MasterStatus enum (INTERVIEW_SCHEDULES).
UPDATE master_steps
SET status_key = 'INTERVIEW_SCHEDULES',
    label = 'Interview Schedules',
    is_default_step = TRUE,
    is_active = TRUE,
    is_visible = FALSE
WHERE status_key IN ('INTERVIEW_SESSION', 'SCHEDULED');

-- REOPEN is not part of MasterStatus; keep row for history but remove from active use.
UPDATE master_steps
SET is_active = FALSE,
    is_default_step = FALSE,
    is_visible = FALSE
WHERE status_key = 'REOPEN';
