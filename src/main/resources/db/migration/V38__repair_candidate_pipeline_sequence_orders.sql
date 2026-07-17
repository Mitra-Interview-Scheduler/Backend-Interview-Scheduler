-- V38__repair_candidate_pipeline_sequence_orders.sql
-- Renumber pipeline steps per candidate to unique 1..N sequence_order values.
-- Fixes rows created when master step_order was reused (e.g. multiple steps at order 3).

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY candidate_id
               ORDER BY sequence_order, id
           ) AS new_sequence_order
    FROM candidate_pipeline_steps
)
UPDATE candidate_pipeline_steps cps
SET sequence_order = ranked.new_sequence_order,
    updated_at = CURRENT_TIMESTAMP
FROM ranked
WHERE cps.id = ranked.id
  AND cps.sequence_order <> ranked.new_sequence_order;
