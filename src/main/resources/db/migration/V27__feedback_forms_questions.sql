CREATE TABLE feedback_forms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    department_ids_json TEXT NOT NULL DEFAULT '[]',
    designation_ids_json TEXT NOT NULL DEFAULT '[]',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE feedback_questions (
    id BIGSERIAL PRIMARY KEY,
    form_id BIGINT NOT NULL REFERENCES feedback_forms(id),
    display_order INT NOT NULL,
    label VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    type VARCHAR(30) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    comments_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    placeholder VARCHAR(255),
    help_text VARCHAR(500),
    options_json TEXT NOT NULL DEFAULT '[]',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE feedback_responses (
    id BIGSERIAL PRIMARY KEY,
    interview_schedule_id BIGINT NOT NULL REFERENCES interview_schedules(id),
    interviewer_id BIGINT NOT NULL REFERENCES users(id),
    form_id BIGINT NOT NULL REFERENCES feedback_forms(id),
    responses_json TEXT NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_feedback_response_schedule_interviewer UNIQUE (interview_schedule_id, interviewer_id)
);

INSERT INTO feedback_forms (name, description, department_ids_json, designation_ids_json, is_active)
VALUES (
    'Standard Interview Feedback',
    'Comprehensive feedback form for interview evaluations',
    '[]',
    '[]',
    TRUE
);

INSERT INTO feedback_questions (
    form_id,
    display_order,
    label,
    category,
    type,
    required,
    comments_enabled,
    placeholder,
    help_text,
    options_json,
    is_active
)
VALUES
((SELECT id FROM feedback_forms WHERE is_active = TRUE ORDER BY id DESC LIMIT 1), 1, 'Technical Skills', 'Technical', 'rating', TRUE, TRUE, 'Rate 1-5', 'Assess the candidate''s technical knowledge and problem-solving abilities', '[{"value":1,"label":"1 - Poor"},{"value":2,"label":"2 - Fair"},{"value":3,"label":"3 - Good"},{"value":4,"label":"4 - Very Good"},{"value":5,"label":"5 - Excellent"}]', TRUE),
((SELECT id FROM feedback_forms WHERE is_active = TRUE ORDER BY id DESC LIMIT 1), 2, 'Communication Skills', 'Soft Skills', 'rating', TRUE, TRUE, 'Rate 1-5', 'Evaluate the candidate''s ability to articulate ideas clearly', '[{"value":1,"label":"1 - Poor"},{"value":2,"label":"2 - Fair"},{"value":3,"label":"3 - Good"},{"value":4,"label":"4 - Very Good"},{"value":5,"label":"5 - Excellent"}]', TRUE),
((SELECT id FROM feedback_forms WHERE is_active = TRUE ORDER BY id DESC LIMIT 1), 3, 'Problem-Solving Approach', 'Technical', 'rating', TRUE, TRUE, 'Rate 1-5', 'Assess how the candidate approaches and solves complex problems', '[{"value":1,"label":"1 - Poor"},{"value":2,"label":"2 - Fair"},{"value":3,"label":"3 - Good"},{"value":4,"label":"4 - Very Good"},{"value":5,"label":"5 - Excellent"}]', TRUE),
((SELECT id FROM feedback_forms WHERE is_active = TRUE ORDER BY id DESC LIMIT 1), 4, 'Cultural Fit', 'Cultural', 'rating', TRUE, TRUE, 'Rate 1-5', 'Evaluate the candidate''s alignment with company values and team dynamics', '[{"value":1,"label":"1 - Poor"},{"value":2,"label":"2 - Fair"},{"value":3,"label":"3 - Good"},{"value":4,"label":"4 - Very Good"},{"value":5,"label":"5 - Excellent"}]', TRUE),
((SELECT id FROM feedback_forms WHERE is_active = TRUE ORDER BY id DESC LIMIT 1), 5, 'Recommendation', 'Outcome', 'select', TRUE, TRUE, 'Select recommendation', 'Overall recommendation for this candidate', '[{"value":"RECOMMENDED","label":"Recommended for next round"},{"value":"HOLD","label":"Hold - needs consideration"},{"value":"REJECTED","label":"Not recommended"}]', TRUE),
((SELECT id FROM feedback_forms WHERE is_active = TRUE ORDER BY id DESC LIMIT 1), 6, 'Additional Comments', 'Educational Background', 'textarea', FALSE, FALSE, 'Share any additional observations or notes about the candidate...', 'Provide detailed feedback for the hiring team', '[]', TRUE);
