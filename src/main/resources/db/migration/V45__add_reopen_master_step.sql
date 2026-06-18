-- V45__add_reopen_master_step.sql

INSERT INTO master_steps (
    status_key,
    label,
    step_order,
    display_order,
    bg_color,
    badge_class,
    light_class,
    is_active,
    is_closing_step,
    is_default_step,
    is_visible
)
VALUES (
    'REOPEN',
    'Reopened',
    4,
    105,
    '#6366f1',
    'bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200',
    'bg-indigo-100',
    TRUE,
    FALSE,
    FALSE,
    TRUE
)
ON CONFLICT (status_key) DO UPDATE SET
    label = EXCLUDED.label,
    step_order = EXCLUDED.step_order,
    display_order = EXCLUDED.display_order,
    bg_color = EXCLUDED.bg_color,
    badge_class = EXCLUDED.badge_class,
    light_class = EXCLUDED.light_class,
    is_active = EXCLUDED.is_active,
    is_closing_step = EXCLUDED.is_closing_step,
    is_default_step = EXCLUDED.is_default_step,
    is_visible = EXCLUDED.is_visible;
