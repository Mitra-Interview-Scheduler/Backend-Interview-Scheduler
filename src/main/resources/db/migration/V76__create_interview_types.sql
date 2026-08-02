CREATE TABLE interview_types (
    id                        BIGSERIAL PRIMARY KEY,
    code                      VARCHAR(64)  NOT NULL UNIQUE,
    label                     VARCHAR(255) NOT NULL,
    description               VARCHAR(1000),
    active                    BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order             INTEGER      NOT NULL DEFAULT 0,
    is_system                 BOOLEAN      NOT NULL DEFAULT FALSE,
    round_status_key          VARCHAR(64),
    cancel_restore_status_key VARCHAR(64)
);

-- Seed the two built-in types, preserving the current enum behaviour:
--   TECHNICAL -> advances candidate to TECHNICAL_ROUND, restores to SCREENING on cancel
--   HR        -> advances candidate to HR_ROUND,        restores to TECHNICAL_ROUND on cancel
INSERT INTO interview_types
    (code, label, description, active, display_order, is_system, round_status_key, cancel_restore_status_key)
VALUES
    ('TECHNICAL', 'Technical Interview', 'Technical evaluation round', TRUE, 1, TRUE, 'TECHNICAL_ROUND', 'SCREENING'),
    ('HR',        'HR Interview',        'HR discussion round',        TRUE, 2, TRUE, 'HR_ROUND',        'TECHNICAL_ROUND');
