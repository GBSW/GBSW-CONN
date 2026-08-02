ALTER TABLE security_throttle_states
    ADD CONSTRAINT ck_security_throttle_scope CHECK (
        throttle_scope IN (
            'LOGIN_ACCOUNT', 'LOGIN_IP',
            'ACTIVATION_ACCOUNT', 'ACTIVATION_IP',
            'PASSWORD_RESET_ACCOUNT', 'PASSWORD_RESET_IP'
        )
    );

ALTER TABLE audit_logs
    ADD CONSTRAINT ck_audit_logs_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'BLOCKED'));

CREATE INDEX ix_activation_codes_expiry ON activation_codes (expires_at);
CREATE INDEX ix_password_reset_tokens_expiry ON password_reset_tokens (expires_at);
