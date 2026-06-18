-- V52__question_categories.sql

CREATE TABLE IF NOT EXISTS question_categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO question_categories (code, label, display_order, is_active, is_system)
VALUES
    ('OBLIGATORY', 'Obligatory', 0, TRUE, TRUE),
    ('EDUCATIONAL_BACKGROUND', 'Educational Background', 1, TRUE, FALSE),
    ('RELEVANT_EXPERIENCE', 'Relevant Experience', 2, TRUE, FALSE),
    ('ARCHITECTURE_SYSTEMS_DESIGN', 'Architecture & Systems Design', 3, TRUE, FALSE),
    ('SOFTWARE_DEVELOPMENT', 'Software Development & Programming', 4, TRUE, FALSE),
    ('METHODOLOGIES_TOOLS', 'Methodologies & Tools', 5, TRUE, FALSE),
    ('TECHNICAL_EXPERTISE', 'Technical Expertise', 6, TRUE, FALSE),
    ('CONCEPTUAL_UNDERSTANDING', 'Conceptual Understanding', 7, TRUE, FALSE),
    ('ANALYTICAL_PROBLEM_SOLVING', 'Analytical and Problem Solving Skills', 8, TRUE, FALSE),
    ('TEAMWORK', 'Teamwork', 9, TRUE, FALSE),
    ('LEADERSHIP', 'Leadership', 10, TRUE, FALSE),
    ('GROWTH_POTENTIAL', 'Growth Potential and Achievements', 11, TRUE, FALSE),
    ('COMMUNICATION_SKILLS', 'Communication Skills', 12, TRUE, FALSE),
    ('TECHNICAL', 'Technical', 20, TRUE, FALSE),
    ('SOFT_SKILLS', 'Soft Skills', 21, TRUE, FALSE),
    ('CULTURAL', 'Cultural', 22, TRUE, FALSE),
    ('OUTCOME', 'Outcome', 23, TRUE, FALSE),
    ('EXPERIENCE', 'Experience', 24, TRUE, FALSE),
    ('COMPENSATION', 'Compensation', 25, TRUE, FALSE),
    ('PERSONAL', 'Personal', 26, TRUE, FALSE),
    ('FEEDBACK', 'Feedback', 27, TRUE, FALSE),
    ('GENERAL', 'General', 99, TRUE, FALSE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO question_categories (code, label, display_order, is_active, is_system)
SELECT
    UPPER(
        LEFT(
            REGEXP_REPLACE(REGEXP_REPLACE(TRIM(fq.category), '[^a-zA-Z0-9]+', '_', 'g'), '(^_+|_+$)', '', 'g'),
            50
        )
    ),
    TRIM(fq.category),
    50,
    TRUE,
    FALSE
FROM feedback_questions fq
WHERE fq.category IS NOT NULL
  AND TRIM(fq.category) <> ''
  AND LOWER(TRIM(fq.category)) <> 'obligatory'
  AND NOT EXISTS (
      SELECT 1
      FROM question_categories qc
      WHERE LOWER(qc.label) = LOWER(TRIM(fq.category))
  )
GROUP BY TRIM(fq.category)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE feedback_questions
    ADD COLUMN IF NOT EXISTS category_id BIGINT REFERENCES question_categories(id);

ALTER TABLE feedback_questions
    ADD COLUMN IF NOT EXISTS is_obligatory BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE feedback_questions
SET is_obligatory = TRUE
WHERE LOWER(TRIM(category)) = 'obligatory';

UPDATE feedback_questions fq
SET category_id = qc.id
FROM question_categories qc
WHERE fq.category_id IS NULL
  AND fq.category IS NOT NULL
  AND LOWER(TRIM(fq.category)) = LOWER(qc.label);

UPDATE feedback_questions fq
SET category_id = qc.id
FROM question_categories qc
WHERE fq.category_id IS NULL
  AND fq.category IS NOT NULL
  AND LOWER(TRIM(fq.category)) = LOWER(qc.code);

UPDATE feedback_questions
SET category_id = (SELECT id FROM question_categories WHERE code = 'OBLIGATORY')
WHERE category_id IS NULL
  AND is_obligatory = TRUE;

UPDATE feedback_questions
SET category_id = (SELECT id FROM question_categories WHERE code = 'GENERAL')
WHERE category_id IS NULL;

ALTER TABLE feedback_questions
    ALTER COLUMN category_id SET NOT NULL;

ALTER TABLE feedback_questions
    DROP CONSTRAINT IF EXISTS chk_form_id_requires_obligatory_category;

ALTER TABLE feedback_questions
    ADD CONSTRAINT chk_form_id_requires_obligatory_category
        CHECK (form_id IS NOT NULL OR is_obligatory = TRUE);

ALTER TABLE feedback_questions
    DROP COLUMN IF EXISTS category;

CREATE INDEX IF NOT EXISTS idx_feedback_questions_category_id
    ON feedback_questions(category_id);

CREATE INDEX IF NOT EXISTS idx_feedback_questions_is_obligatory
    ON feedback_questions(is_obligatory)
    WHERE is_obligatory = TRUE;
