-- V50__add_offer_pending_master_step.sql
-- Stage between disposition and final selection: HR extended offer, awaiting candidate confirmation.

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
    'OFFER_PENDING',
    'Awaiting Offer',
    4,
    65,
    '#8b5cf6',
    'bg-violet-100 text-violet-800 dark:bg-violet-900 dark:text-violet-200',
    'bg-violet-100',
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
