-- Rename candidate master step SCHEDULED to INTERVIEW_SCHEDULES to distinguish
-- it from interview_schedules.status (InterviewStatus.SCHEDULED).

UPDATE master_steps
SET status_key = 'INTERVIEW_SCHEDULES',
    label = 'Interview Schedules'
WHERE status_key = 'SCHEDULED';