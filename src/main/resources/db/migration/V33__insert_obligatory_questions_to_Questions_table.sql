-- 1. Remove the strict NOT NULL constraint from the column
ALTER TABLE feedback_questions ALTER COLUMN form_id DROP NOT NULL;

-- 2. Add a CHECK constraint to enforce the conditional logic
ALTER TABLE feedback_questions ADD CONSTRAINT chk_form_id_requires_obligatory_category CHECK (form_id IS NOT NULL OR category = 'obligatory');

-- 3. Insert the obligatory global questions
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
    (
        NULL, 1, 'Overall Rating', 'obligatory', 'dropdown', TRUE, TRUE,
        'Select an overall rating',
        'Provide a comprehensive evaluation of the candidate based on all aspects of the interview.',
        '[{"value":1,"label":"1 - Poor"},{"value":2,"label":"2 - Fair"},{"value":3,"label":"3 - Good"},{"value":4,"label":"4 - Very Good"},{"value":5,"label":"5 - Excellent"}]',
        TRUE
    ),
    (
        NULL, 2, 'Decision on Hire', 'obligatory', 'dropdown', TRUE, TRUE,
        'Select hiring decision',
        'Indicate your final recommendation on whether to proceed with this candidate.',
        '[{"value":"Yes","label":"Yes"},{"value":"No","label":"No"},{"value":"Differed","label":"Differed"}]',
        TRUE
    ),
    (
        NULL, 3, 'Remarks', 'obligatory', 'text', TRUE, FALSE,
        'Enter detailed remarks and justification...',
        'Summarize your primary reasons for the hiring decision and your overall impression of the candidate.',
        '[]',
        TRUE
    ),
    (
        NULL, 4, 'Training Needs', 'obligatory', 'dropdown', TRUE, TRUE,
        'Select required training timeline',
        'Indicate whether the candidate will require immediate upskilling upon joining or long-term development.',
        '[{"value":"Immediate","label":"Immediate"},{"value":"LongTerm","label":"Long Term"}]',
        TRUE
    ),
    (
        NULL, 5, 'Notes to HR', 'obligatory', 'text', TRUE, FALSE,
        'Enter confidential notes for Human Resources...',
        'Include details regarding compensation expectations, notice period, flight risks, or behavioral red flags.',
        '[]',
        TRUE
    ),
    (
        NULL, 6, 'Notes to Next Interviewer', 'obligatory', 'text', TRUE, FALSE,
        'Enter topics for the next interviewer to cover...',
        'Highlight specific technical areas, past projects, or soft skills the next round should dive deeper into.',
        '[]',
        TRUE
    );