-- Replace candidates.status with master_step_id FK to master_steps.id.

ALTER TABLE candidates
    ADD COLUMN IF NOT EXISTS master_step_id BIGINT;

UPDATE candidates c
SET master_step_id = ms.id
FROM master_steps ms
WHERE c.master_step_id IS NULL
  AND UPPER(TRIM(c.status)) = ms.status_key;

UPDATE candidates c
SET master_step_id = ms.id
FROM master_steps ms
WHERE c.master_step_id IS NULL
  AND c.status IN ('APPLIED', 'applied')
  AND ms.status_key = 'NEW';

UPDATE candidates c
SET master_step_id = ms.id
FROM master_steps ms
WHERE c.master_step_id IS NULL
  AND ms.status_key = 'NEW';

ALTER TABLE candidates
    DROP COLUMN IF EXISTS status;

ALTER TABLE candidates
    ALTER COLUMN master_step_id SET NOT NULL;

ALTER TABLE candidates
    ADD CONSTRAINT fk_candidates_master_step
        FOREIGN KEY (master_step_id) REFERENCES master_steps (id);

DROP INDEX IF EXISTS idx_candidates_status;

CREATE INDEX IF NOT EXISTS idx_candidates_master_step
    ON candidates (master_step_id, is_active);
