-- Ensure core pipeline template steps are marked as default + active so
-- initializeDefaultPipeline can seed candidate_pipeline_steps on create.

UPDATE master_steps
SET is_default_step = TRUE,
    is_active = TRUE
WHERE status_key IN ('NEW', 'SCREENING', 'INTERVIEW_SCHEDULES', 'DISPOSITION');

-- INTERVIEW_SCHEDULES stays invisible in the progress UI but belongs in the pipeline.
UPDATE master_steps
SET is_visible = FALSE
WHERE status_key = 'INTERVIEW_SCHEDULES';

UPDATE master_steps
SET is_visible = FALSE
WHERE status_key = 'DISPOSITION';
