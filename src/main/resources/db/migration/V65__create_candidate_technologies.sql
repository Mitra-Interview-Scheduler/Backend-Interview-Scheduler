CREATE TABLE candidate_technologies (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    technology_id BIGINT NOT NULL REFERENCES technologies(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_candidate_technologies_candidate_technology UNIQUE (candidate_id, technology_id)
);

CREATE INDEX idx_candidate_technologies_candidate_id ON candidate_technologies(candidate_id);
CREATE INDEX idx_candidate_technologies_technology_id ON candidate_technologies(technology_id);
