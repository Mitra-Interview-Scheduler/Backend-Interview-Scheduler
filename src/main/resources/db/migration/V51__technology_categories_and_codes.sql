-- V51__technology_categories_and_codes.sql
-- Normalize technology categories into a lookup table and add short codes on technologies.

CREATE TABLE IF NOT EXISTS technology_categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO technology_categories (code, label, display_order, is_active)
VALUES
    ('PROG_LANG', 'Programming Language', 1, TRUE),
    ('FRAMEWORK', 'Framework', 2, TRUE),
    ('CLOUD', 'Cloud Platform', 3, TRUE),
    ('DEVOPS', 'DevOps', 4, TRUE),
    ('ARCHITECTURE', 'Architecture', 5, TRUE),
    ('RUNTIME', 'Runtime', 6, TRUE),
    ('DATABASE', 'Database', 7, TRUE),
    ('CACHE', 'Cache', 8, TRUE),
    ('CONCEPT', 'Concept', 9, TRUE),
    ('GENERAL', 'General', 99, TRUE)
ON CONFLICT (code) DO NOTHING;

-- Preserve any custom categories already stored as free text on technologies.
INSERT INTO technology_categories (code, label, display_order, is_active)
SELECT
    UPPER(
        LEFT(
            REGEXP_REPLACE(REGEXP_REPLACE(TRIM(t.category), '[^a-zA-Z0-9]+', '_', 'g'), '(^_+|_+$)', '', 'g'),
            50
        )
    ),
    TRIM(t.category),
    50,
    TRUE
FROM technologies t
WHERE t.category IS NOT NULL
  AND TRIM(t.category) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM technology_categories tc
      WHERE LOWER(tc.label) = LOWER(TRIM(t.category))
  )
GROUP BY TRIM(t.category)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE technologies
    ADD COLUMN IF NOT EXISTS category_id BIGINT REFERENCES technology_categories(id);

UPDATE technologies t
SET category_id = tc.id
FROM technology_categories tc
WHERE t.category_id IS NULL
  AND t.category IS NOT NULL
  AND LOWER(TRIM(t.category)) = LOWER(TRIM(tc.label));

UPDATE technologies
SET category_id = (SELECT id FROM technology_categories WHERE code = 'GENERAL')
WHERE category_id IS NULL;

ALTER TABLE technologies
    ADD COLUMN IF NOT EXISTS code VARCHAR(50);

UPDATE technologies SET code = 'JAVA' WHERE code IS NULL AND name = 'Java';
UPDATE technologies SET code = 'SPRING_BOOT' WHERE code IS NULL AND name = 'Spring Boot';
UPDATE technologies SET code = 'REACT' WHERE code IS NULL AND name = 'React';
UPDATE technologies SET code = 'TYPESCRIPT' WHERE code IS NULL AND name = 'TypeScript';
UPDATE technologies SET code = 'AWS' WHERE code IS NULL AND name = 'AWS';
UPDATE technologies SET code = 'DOCKER' WHERE code IS NULL AND name = 'Docker';
UPDATE technologies SET code = 'MICROSERVICES' WHERE code IS NULL AND name = 'Microservices';
UPDATE technologies SET code = 'PYTHON' WHERE code IS NULL AND name = 'Python';
UPDATE technologies SET code = 'NODE_JS' WHERE code IS NULL AND name = 'Node.js';
UPDATE technologies SET code = 'ANGULAR' WHERE code IS NULL AND name = 'Angular';
UPDATE technologies SET code = 'VUE_JS' WHERE code IS NULL AND name = 'Vue.js';
UPDATE technologies SET code = 'POSTGRESQL' WHERE code IS NULL AND name = 'PostgreSQL';
UPDATE technologies SET code = 'MONGODB' WHERE code IS NULL AND name = 'MongoDB';
UPDATE technologies SET code = 'REDIS' WHERE code IS NULL AND name = 'Redis';
UPDATE technologies SET code = 'KUBERNETES' WHERE code IS NULL AND name = 'Kubernetes';
UPDATE technologies SET code = 'SYSTEM_DESIGN' WHERE code IS NULL AND name = 'System Design';

UPDATE technologies
SET code = UPPER(
    LEFT(
        REGEXP_REPLACE(REGEXP_REPLACE(name, '[^a-zA-Z0-9]+', '_', 'g'), '(^_+|_+$)', '', 'g'),
        50
    )
)
WHERE code IS NULL;

UPDATE technologies t
SET code = code || '_' || t.id
WHERE EXISTS (
    SELECT 1
    FROM technologies t2
    WHERE t2.code = t.code
      AND t2.id <> t.id
);

ALTER TABLE technologies
    ALTER COLUMN code SET NOT NULL;

ALTER TABLE technologies
    ADD CONSTRAINT uk_technologies_code UNIQUE (code);

ALTER TABLE technologies
    ALTER COLUMN category_id SET NOT NULL;

ALTER TABLE technologies
    DROP COLUMN IF EXISTS category;

CREATE INDEX IF NOT EXISTS idx_technologies_category_id
    ON technologies(category_id);
