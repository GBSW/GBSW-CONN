CREATE TABLE privileged_change_requests (
    id BINARY(16) NOT NULL,
    public_id BINARY(16) NOT NULL,
    change_type VARCHAR(48) NOT NULL,
    change_status VARCHAR(16) NOT NULL,
    target_user_id BINARY(16) NULL,
    login_id VARCHAR(100) NULL,
    display_name VARCHAR(100) NULL,
    role_type VARCHAR(32) NULL,
    office_type VARCHAR(64) NULL,
    starts_at TIMESTAMP(6) NULL,
    ends_at TIMESTAMP(6) NULL,
    replace_existing_at_start BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(2000) NOT NULL,
    requested_by_user_id BINARY(16) NOT NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    approved_by_user_id BINARY(16) NULL,
    approval_reason VARCHAR(2000) NULL,
    approved_at TIMESTAMP(6) NULL,
    executed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_privileged_change_public_id (public_id),
    KEY ix_privileged_change_status_expiry (change_status, expires_at),
    KEY ix_privileged_change_target (target_user_id, requested_at),
    CONSTRAINT ck_privileged_change_type CHECK (
        change_type IN (
            'CREATE_ACCOUNT', 'REISSUE_ACTIVATION_CODE', 'ISSUE_PASSWORD_RESET_CODE',
            'ASSIGN_ROLE', 'END_ROLE', 'APPOINT_OFFICE', 'END_OFFICE'
        )
    ),
    CONSTRAINT ck_privileged_change_status CHECK (change_status IN ('PENDING', 'EXECUTED', 'EXPIRED')),
    CONSTRAINT ck_privileged_change_distinct_approver CHECK (
        approved_by_user_id IS NULL OR approved_by_user_id <> requested_by_user_id
    ),
    CONSTRAINT ck_privileged_change_period CHECK (
        starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at
    ),
    CONSTRAINT ck_privileged_change_execution CHECK (
        (change_status = 'PENDING' AND approved_by_user_id IS NULL AND executed_at IS NULL)
        OR (change_status = 'EXECUTED' AND approved_by_user_id IS NOT NULL AND executed_at IS NOT NULL)
        OR (change_status = 'EXPIRED' AND executed_at IS NULL)
    ),
    CONSTRAINT ck_privileged_change_typed_fields CHECK (
        (change_type = 'CREATE_ACCOUNT' AND login_id IS NOT NULL AND display_name IS NOT NULL AND role_type IS NOT NULL)
        OR (change_type IN ('REISSUE_ACTIVATION_CODE', 'ISSUE_PASSWORD_RESET_CODE') AND target_user_id IS NOT NULL)
        OR (change_type IN ('ASSIGN_ROLE', 'END_ROLE') AND target_user_id IS NOT NULL AND role_type IS NOT NULL)
        OR (change_type IN ('APPOINT_OFFICE', 'END_OFFICE') AND target_user_id IS NOT NULL AND office_type IS NOT NULL)
    ),
    CONSTRAINT fk_privileged_change_target FOREIGN KEY (target_user_id) REFERENCES users (id),
    CONSTRAINT fk_privileged_change_requester FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_privileged_change_approver FOREIGN KEY (approved_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
