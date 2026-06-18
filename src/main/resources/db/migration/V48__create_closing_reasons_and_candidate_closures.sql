-- V48__create_closing_reasons_and_candidate_closures.sql

CREATE TABLE IF NOT EXISTS closing_reasons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS candidate_closures (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL REFERENCES candidates(id),
    closing_reason_id BIGINT NOT NULL REFERENCES closing_reasons(id),
    closed_status_key VARCHAR(50) NOT NULL,
    comment TEXT,
    closed_by BIGINT REFERENCES users(id),
    closed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_candidate_closures_candidate_id
    ON candidate_closures(candidate_id);

CREATE INDEX IF NOT EXISTS idx_candidate_closures_closed_at
    ON candidate_closures(candidate_id, closed_at DESC);

INSERT INTO closing_reasons (code, label, display_order, is_active)
VALUES
    ('SKILLS_MISMATCH', 'Skills mismatch', 1, TRUE),
    ('CULTURAL_FIT', 'Cultural fit concerns', 2, TRUE),
    ('SALARY_EXPECTATIONS', 'Salary expectations', 3, TRUE),
    ('POSITION_FILLED', 'Position filled', 4, TRUE),
    ('CANDIDATE_WITHDREW', 'Candidate withdrew', 5, TRUE),
    ('NO_SHOW', 'No show / unresponsive', 6, TRUE),
    ('OFFER_DECLINED', 'Offer declined', 7, TRUE),
    ('OTHER', 'Other', 99, TRUE)
ON CONFLICT (code) DO NOTHING;
