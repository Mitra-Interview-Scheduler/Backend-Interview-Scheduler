ALTER TABLE candidate_pipeline_status_events
    ADD COLUMN IF NOT EXISTS changed_by_designation VARCHAR(255);
