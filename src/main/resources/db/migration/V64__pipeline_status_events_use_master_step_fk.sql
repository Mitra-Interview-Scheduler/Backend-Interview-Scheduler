-- Replace status_key strings with master_step_id FKs and drop denormalized actor columns.

ALTER TABLE candidate_pipeline_status_events
    ADD COLUMN IF NOT EXISTS master_step_id BIGINT,
    ADD COLUMN IF NOT EXISTS previous_master_step_id BIGINT;

UPDATE candidate_pipeline_status_events e
SET master_step_id = ms.id
FROM master_steps ms
WHERE e.status_key = ms.status_key
  AND e.master_step_id IS NULL;

UPDATE candidate_pipeline_status_events e
SET previous_master_step_id = ms.id
FROM master_steps ms
WHERE e.previous_status_key = ms.status_key
  AND e.previous_master_step_id IS NULL;

DELETE FROM candidate_pipeline_status_events
WHERE master_step_id IS NULL;

ALTER TABLE candidate_pipeline_status_events
    ALTER COLUMN master_step_id SET NOT NULL;

ALTER TABLE candidate_pipeline_status_events
    DROP CONSTRAINT IF EXISTS fk_pipeline_event_master_step;

ALTER TABLE candidate_pipeline_status_events
    ADD CONSTRAINT fk_pipeline_event_master_step
        FOREIGN KEY (master_step_id) REFERENCES master_steps (id);

ALTER TABLE candidate_pipeline_status_events
    DROP CONSTRAINT IF EXISTS fk_pipeline_event_previous_master_step;

ALTER TABLE candidate_pipeline_status_events
    ADD CONSTRAINT fk_pipeline_event_previous_master_step
        FOREIGN KEY (previous_master_step_id) REFERENCES master_steps (id);

ALTER TABLE candidate_pipeline_status_events
    DROP COLUMN IF EXISTS status_key,
    DROP COLUMN IF EXISTS previous_status_key,
    DROP COLUMN IF EXISTS changed_by_name,
    DROP COLUMN IF EXISTS changed_by_designation;

CREATE INDEX IF NOT EXISTS idx_pipeline_status_events_master_step
    ON candidate_pipeline_status_events (master_step_id);
