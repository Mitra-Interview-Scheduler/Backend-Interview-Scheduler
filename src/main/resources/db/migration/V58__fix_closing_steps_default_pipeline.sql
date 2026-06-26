-- Closing outcome steps must never be seeded into the default hiring pipeline.
UPDATE master_steps
SET is_default_step = FALSE
WHERE is_closing_step = TRUE
  AND status_key NOT IN ('INTERVIEW_SCHEDULES', 'DISPOSITION');
