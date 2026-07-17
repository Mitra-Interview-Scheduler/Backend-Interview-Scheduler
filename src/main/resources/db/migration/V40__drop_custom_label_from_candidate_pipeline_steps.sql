-- Remove per-row custom labels; pipeline step labels come from master_steps.

ALTER TABLE candidate_pipeline_steps
    DROP COLUMN IF EXISTS custom_label;
