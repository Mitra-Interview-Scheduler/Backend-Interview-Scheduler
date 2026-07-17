-- Replace candidate_pipeline_steps.status_key with master_step_id FK to master_steps.id.

ALTER TABLE candidate_pipeline_steps
    ADD COLUMN IF NOT EXISTS master_step_id BIGINT;

UPDATE candidate_pipeline_steps cps
SET master_step_id = ms.id
FROM master_steps ms
WHERE cps.status_key = ms.status_key
  AND cps.master_step_id IS NULL;

DELETE FROM candidate_pipeline_steps
WHERE master_step_id IS NULL;

ALTER TABLE candidate_pipeline_steps
    DROP CONSTRAINT IF EXISTS fk_pipeline_steps_master_step;

ALTER TABLE candidate_pipeline_steps
    DROP COLUMN IF EXISTS status_key;

ALTER TABLE candidate_pipeline_steps
    ALTER COLUMN master_step_id SET NOT NULL;

ALTER TABLE candidate_pipeline_steps
    ADD CONSTRAINT fk_candidate_pipeline_master_step
        FOREIGN KEY (master_step_id) REFERENCES master_steps (id);

CREATE INDEX IF NOT EXISTS idx_candidate_pipeline_master_step
    ON candidate_pipeline_steps (master_step_id);
