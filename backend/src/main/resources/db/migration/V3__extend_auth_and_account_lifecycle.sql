ALTER TABLE security_throttle_states
    DROP CHECK ck_security_throttle_scope,
    ADD CONSTRAINT ck_security_throttle_scope CHECK (
        throttle_scope IN (
            'LOGIN_ACCOUNT', 'LOGIN_IP',
            'ACTIVATION_ACCOUNT', 'ACTIVATION_IP',
            'PASSWORD_RESET_ACCOUNT', 'PASSWORD_RESET_IP',
            'REAUTHENTICATION_ACCOUNT', 'REAUTHENTICATION_IP'
        )
    );

CREATE INDEX ix_users_status_created ON users (account_status, created_at);

ALTER TABLE role_assignments
    ADD COLUMN ended_by_user_id BINARY(16) NULL AFTER reason,
    ADD COLUMN ended_at TIMESTAMP(6) NULL AFTER ended_by_user_id,
    ADD COLUMN end_reason VARCHAR(500) NULL AFTER ended_at,
    ADD CONSTRAINT fk_role_assignments_ended_by
        FOREIGN KEY (ended_by_user_id) REFERENCES users (id);

ALTER TABLE office_assignments
    ADD COLUMN ended_by_user_id BINARY(16) NULL AFTER reason,
    ADD COLUMN ended_at TIMESTAMP(6) NULL AFTER ended_by_user_id,
    ADD COLUMN end_reason VARCHAR(500) NULL AFTER ended_at,
    ADD CONSTRAINT fk_office_assignments_ended_by
        FOREIGN KEY (ended_by_user_id) REFERENCES users (id);

CREATE TABLE bootstrap_markers (
    marker_name VARCHAR(64) NOT NULL,
    completed_by_user_id BINARY(16) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (marker_name),
    CONSTRAINT fk_bootstrap_markers_user
        FOREIGN KEY (completed_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
