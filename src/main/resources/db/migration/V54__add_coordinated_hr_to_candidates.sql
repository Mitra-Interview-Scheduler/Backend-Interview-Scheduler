ALTER TABLE candidates
  ADD COLUMN coordinated_hr_id BIGINT REFERENCES users(id);

CREATE INDEX idx_candidates_coordinated_hr ON candidates (coordinated_hr_id);
