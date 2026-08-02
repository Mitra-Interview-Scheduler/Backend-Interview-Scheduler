-- Interviewer matching rules on interview types.
-- Modes: SAME_AS_CANDIDATE | FIXED | NONE

ALTER TABLE interview_types
    ADD COLUMN IF NOT EXISTS department_filter_mode VARCHAR(32) NOT NULL DEFAULT 'SAME_AS_CANDIDATE',
    ADD COLUMN IF NOT EXISTS fixed_department_id BIGINT REFERENCES departments (id),
    ADD COLUMN IF NOT EXISTS min_years_experience INTEGER,
    ADD COLUMN IF NOT EXISTS tier_filter_mode VARCHAR(32) NOT NULL DEFAULT 'SAME_AS_CANDIDATE',
    ADD COLUMN IF NOT EXISTS fixed_min_tier_id BIGINT REFERENCES tiers (id),
    ADD COLUMN IF NOT EXISTS designation_filter_mode VARCHAR(32) NOT NULL DEFAULT 'SAME_AS_CANDIDATE',
    ADD COLUMN IF NOT EXISTS fixed_min_designation_id BIGINT REFERENCES designations (id),
    ADD COLUMN IF NOT EXISTS domain_filter_mode VARCHAR(32) NOT NULL DEFAULT 'SAME_AS_CANDIDATE',
    ADD COLUMN IF NOT EXISTS category_filter_mode VARCHAR(32) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS technology_filter_mode VARCHAR(32) NOT NULL DEFAULT 'SAME_AS_CANDIDATE';

CREATE TABLE IF NOT EXISTS interview_type_fixed_domains (
    interview_type_id BIGINT NOT NULL REFERENCES interview_types (id) ON DELETE CASCADE,
    domain_id         BIGINT NOT NULL REFERENCES domains (id) ON DELETE CASCADE,
    PRIMARY KEY (interview_type_id, domain_id)
);

CREATE TABLE IF NOT EXISTS interview_type_fixed_categories (
    interview_type_id BIGINT NOT NULL REFERENCES interview_types (id) ON DELETE CASCADE,
    category_id       BIGINT NOT NULL REFERENCES technology_categories (id) ON DELETE CASCADE,
    PRIMARY KEY (interview_type_id, category_id)
);

CREATE TABLE IF NOT EXISTS interview_type_fixed_technologies (
    interview_type_id BIGINT NOT NULL REFERENCES interview_types (id) ON DELETE CASCADE,
    technology_id     BIGINT NOT NULL REFERENCES technologies (id) ON DELETE CASCADE,
    PRIMARY KEY (interview_type_id, technology_id)
);

-- TECHNICAL: match same dept / tier / designation / domains / technologies as candidate
UPDATE interview_types
SET department_filter_mode = 'SAME_AS_CANDIDATE',
    tier_filter_mode = 'SAME_AS_CANDIDATE',
    designation_filter_mode = 'SAME_AS_CANDIDATE',
    domain_filter_mode = 'SAME_AS_CANDIDATE',
    technology_filter_mode = 'SAME_AS_CANDIDATE',
    category_filter_mode = 'NONE',
    min_years_experience = NULL
WHERE code = 'TECHNICAL';

-- HR: fixed HR department; no designation/domain filter; tier & tech same as candidate
UPDATE interview_types
SET department_filter_mode = 'FIXED',
    fixed_department_id = (SELECT id FROM departments WHERE LOWER(name) = LOWER('Human Resources') LIMIT 1),
    tier_filter_mode = 'SAME_AS_CANDIDATE',
    designation_filter_mode = 'NONE',
    domain_filter_mode = 'NONE',
    technology_filter_mode = 'SAME_AS_CANDIDATE',
    category_filter_mode = 'NONE',
    min_years_experience = NULL
WHERE code = 'HR';
