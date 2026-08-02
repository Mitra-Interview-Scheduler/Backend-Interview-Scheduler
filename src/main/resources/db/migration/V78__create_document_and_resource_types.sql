-- Admin-managed catalogs for candidate document types and resource link types.

CREATE TABLE document_types (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL UNIQUE,
    label           VARCHAR(100) NOT NULL,
    display_order   INT          NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE resource_types (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL UNIQUE,
    label           VARCHAR(100) NOT NULL,
    display_order   INT          NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO document_types (code, label, display_order) VALUES
    ('CV', 'CV', 1),
    ('PROFILE', 'Profile Picture', 2),
    ('CERTIFICATE', 'Certificate', 3),
    ('PORTFOLIO', 'Portfolio', 4),
    ('OTHER', 'Other', 5);

INSERT INTO resource_types (code, label, display_order) VALUES
    ('CV', 'CV', 1),
    ('PROFILE_PICTURE', 'Profile Picture', 2),
    ('CERTIFICATE', 'Certificate', 3),
    ('PORTFOLIO', 'Portfolio', 4),
    ('OTHER', 'Other', 5);
