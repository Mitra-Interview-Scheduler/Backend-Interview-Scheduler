-- V13__create_notifications_table.sql
-- V1 created notifications with legacy is_read column naming.
-- Drop and recreate so the entity-matching schema is applied on fresh installs.

DROP TABLE IF EXISTS notifications CASCADE;

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    subject VARCHAR(500) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    sent BOOLEAN NOT NULL DEFAULT false,
    "read" BOOLEAN NOT NULL DEFAULT false,  -- Quote reserved keyword
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    read_at TIMESTAMP,

    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id)
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, "read", created_at DESC);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_unread ON notifications(recipient_id, "read") WHERE "read" = false;