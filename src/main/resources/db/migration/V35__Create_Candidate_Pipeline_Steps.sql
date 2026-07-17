-- ─────────────────────────────────────────────────────────────────────────────
-- Create Candidate Pipeline Steps Table
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE candidate_pipeline_steps
(
    id             BIGSERIAL PRIMARY KEY,
    candidate_id   BIGINT                NOT NULL,
    status_key     VARCHAR(50)           NOT NULL,
    sequence_order INTEGER               NOT NULL,
    step_status    VARCHAR(20)           NOT NULL DEFAULT 'PENDING',
    custom_label   VARCHAR(100),
    created_at     TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- FK constraints linking tracking rows back to parent records safely
    CONSTRAINT fk_pipeline_steps_candidate
        FOREIGN KEY (candidate_id)
            REFERENCES candidates (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_pipeline_steps_master_step
        FOREIGN KEY (status_key)
            REFERENCES master_steps (status_key),

    -- Enforces a unique order track map for an individual candidate profile
    CONSTRAINT uk_candidate_sequence_order
        UNIQUE (candidate_id, sequence_order)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Optimization Lookups
-- ─────────────────────────────────────────────────────────────────────────────

-- Fast index optimization to load specific progress bars into the UI quickly
CREATE INDEX idx_candidate_pipeline_lookup
    ON candidate_pipeline_steps (candidate_id, sequence_order);