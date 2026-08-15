DELETE FROM security_throttle_states;

ALTER TABLE security_throttle_states
    DROP CHECK ck_security_throttle_scope,
    DROP INDEX uk_security_throttle_scope_subject,
    DROP COLUMN subject_fingerprint,
    ADD COLUMN account_user_id BINARY(16) NOT NULL AFTER throttle_scope,
    ADD COLUMN expires_at TIMESTAMP(6) NOT NULL AFTER last_failure_at,
    ADD UNIQUE KEY uk_security_throttle_scope_account (throttle_scope, account_user_id),
    ADD KEY ix_security_throttle_expiry (expires_at, id),
    ADD CONSTRAINT fk_security_throttle_account
        FOREIGN KEY (account_user_id) REFERENCES users (id),
    ADD CONSTRAINT ck_security_throttle_scope CHECK (
        throttle_scope IN (
            'LOGIN_ACCOUNT',
            'ACTIVATION_ACCOUNT',
            'PASSWORD_RESET_ACCOUNT',
            'REAUTHENTICATION_ACCOUNT',
            'IDENTITY_REVEAL_ACCOUNT'
        )
    );

ALTER TABLE audit_logs
    ADD COLUMN retention_class VARCHAR(32) NOT NULL DEFAULT 'SECURITY' AFTER details_json,
    ADD COLUMN occurrence_count BIGINT NOT NULL DEFAULT 1 AFTER retention_class,
    ADD COLUMN aggregation_bucket TIMESTAMP(6) NULL AFTER occurrence_count,
    ADD COLUMN last_occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER aggregation_bucket,
    ADD CONSTRAINT ck_audit_logs_retention_class CHECK (
        retention_class IN ('SECURITY', 'AUTH_TRANSIENT', 'PRIVACY_AUDIT')
    ),
    ADD CONSTRAINT ck_audit_logs_occurrence_count CHECK (occurrence_count > 0),
    ADD UNIQUE KEY uk_audit_logs_auth_aggregation (event_type, outcome, aggregation_bucket),
    ADD KEY ix_audit_logs_retention (retention_class, last_occurred_at, id);

UPDATE audit_logs SET last_occurred_at = created_at;
