-- Add resource request number to candidates
-- Keeps existing rows intact and allows search/filter by the request number.

ALTER TABLE candidates
    ADD COLUMN IF NOT EXISTS resource_request_number VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_candidates_resource_request_number
    ON candidates (resource_request_number);