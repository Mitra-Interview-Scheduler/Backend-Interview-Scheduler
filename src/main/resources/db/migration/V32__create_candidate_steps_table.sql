DROP TABLE IF EXISTS candidate_steps;  

UPDATE candidates
SET status = 'NEW'
WHERE status = 'APPLIED';

ALTER TABLE candidates
ALTER COLUMN status SET DEFAULT 'NEW';

CREATE TABLE IF NOT EXISTS candidate_steps (
    id BIGSERIAL PRIMARY KEY,
    status_key VARCHAR(50) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    step_order INTEGER NOT NULL,
    display_order INTEGER NOT NULL,
    bg_color VARCHAR(20) NOT NULL,
    badge_class VARCHAR(255) NOT NULL,
    light_class VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_closing_step BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_candidate_steps_active_order
    ON candidate_steps (is_active, step_order, display_order);

INSERT INTO candidate_steps (
    status_key, label, step_order, display_order, bg_color, badge_class, light_class, is_active, is_closing_step
) VALUES
    ('NEW', 'New', 1, 10, '#3b82f6', 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200', 'bg-blue-100', TRUE, FALSE),
    ('SCREENING', 'Screening', 2, 20, '#eab308', 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200', 'bg-yellow-100', TRUE, FALSE),
    ('SCHEDULED', 'Scheduled', 3, 30, '#a855f7', 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200', 'bg-purple-100', TRUE, FALSE),
    ('INTERVIEWED', 'Interviewed', 4, 40, '#6366f1', 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200', 'bg-indigo-100', TRUE, FALSE),
    ('TECHNICAL_ROUND', 'Technical', 5, 50, '#06b6d4', 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-200', 'bg-cyan-100', TRUE, FALSE),
    ('HR_ROUND', 'HR Round', 6, 60, '#ec4899', 'bg-pink-100 text-pink-800 dark:bg-pink-900 dark:text-pink-200', 'bg-pink-100', TRUE, FALSE),
    ('SELECTED', 'Selected', 7, 70, '#22c55e', 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200', 'bg-green-100', TRUE, TRUE),
    ('REJECTED', 'Rejected', 7, 80, '#ef4444', 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200', 'bg-red-100', TRUE, TRUE),
    ('ON_HOLD', 'On Hold', 7, 90, '#f97316', 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200', 'bg-orange-100', TRUE, TRUE),
    ('WITHDRAWN', 'Withdrawn', 7, 100, '#6b7280', 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200', 'bg-gray-100', TRUE, TRUE)
ON CONFLICT (status_key) DO UPDATE SET
    label = EXCLUDED.label,
    step_order = EXCLUDED.step_order,
    display_order = EXCLUDED.display_order,
    bg_color = EXCLUDED.bg_color,
    badge_class = EXCLUDED.badge_class,
    light_class = EXCLUDED.light_class,
    is_active = EXCLUDED.is_active,
    is_closing_step = EXCLUDED.is_closing_step;
