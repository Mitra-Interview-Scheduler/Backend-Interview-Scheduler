-- Outbound email delivery audit trail (admin).
CREATE TABLE IF NOT EXISTS email_delivery_logs (
    id              BIGSERIAL PRIMARY KEY,
    subject         VARCHAR(500)  NOT NULL,
    body            TEXT          NOT NULL,
    recipients      TEXT          NOT NULL,
    recipient_name  VARCHAR(255),
    status          VARCHAR(32)   NOT NULL,
    error_message   TEXT,
    sent_at         TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_delivery_logs_sent_at
    ON email_delivery_logs (sent_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_delivery_logs_status
    ON email_delivery_logs (status);
