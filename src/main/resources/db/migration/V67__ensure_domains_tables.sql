-- V66 was recorded in flyway_schema_history but tables were missing (checksum repair only).
CREATE TABLE IF NOT EXISTS domains (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_domains_name UNIQUE (name),
    CONSTRAINT uq_domains_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS user_domains (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    domain_id BIGINT NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_domains UNIQUE (user_id, domain_id)
);

CREATE TABLE IF NOT EXISTS candidate_domains (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    domain_id BIGINT NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    CONSTRAINT uq_candidate_domains UNIQUE (candidate_id, domain_id)
);

CREATE INDEX IF NOT EXISTS idx_user_domains_user_id ON user_domains(user_id);
CREATE INDEX IF NOT EXISTS idx_user_domains_domain_id ON user_domains(domain_id);
CREATE INDEX IF NOT EXISTS idx_candidate_domains_candidate_id ON candidate_domains(candidate_id);
CREATE INDEX IF NOT EXISTS idx_candidate_domains_domain_id ON candidate_domains(domain_id);
