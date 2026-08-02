ALTER TABLE candidates
  ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_candidates_created_by
  ON candidates (created_by_user_id);

-- Backfill from APPLICATION_CREATED ("NWC") pipeline audit events when available
UPDATE candidates c
SET created_by_user_id = e.changed_by_user_id
FROM (
    SELECT DISTINCT ON (candidate_id)
        candidate_id,
        changed_by_user_id
    FROM candidate_pipeline_status_events
    WHERE action_type = 'NWC'
      AND changed_by_user_id IS NOT NULL
    ORDER BY candidate_id, created_at ASC
) e
WHERE c.id = e.candidate_id
  AND c.created_by_user_id IS NULL;
