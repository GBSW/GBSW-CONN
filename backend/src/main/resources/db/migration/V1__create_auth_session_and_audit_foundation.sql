CREATE TABLE users (
    id BINARY(16) NOT NULL,
    public_id BINARY(16) NOT NULL,
    login_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    account_status VARCHAR(32) NOT NULL,
    activated_at TIMESTAMP(6) NULL,
    suspended_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_public_id (public_id),
    UNIQUE KEY uk_users_login_id (login_id),
    CONSTRAINT ck_users_account_status CHECK (account_status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE credentials (
    user_id BINARY(16) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    credential_version BIGINT NOT NULL DEFAULT 1,
    password_changed_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_credentials_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role_assignments (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    role_type VARCHAR(32) NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NULL,
    assigned_by_user_id BINARY(16) NULL,
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_role_assignments_user_period (user_id, starts_at, ends_at),
    CONSTRAINT ck_role_assignments_type CHECK (role_type IN ('STUDENT', 'TEACHER', 'SUPER_ADMIN')),
    CONSTRAINT ck_role_assignments_period CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT fk_role_assignments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_role_assignments_assigner FOREIGN KEY (assigned_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE office_assignments (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    office_type VARCHAR(64) NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NULL,
    assigned_by_user_id BINARY(16) NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_office_assignments_type_period (office_type, starts_at, ends_at),
    KEY ix_office_assignments_user_period (user_id, starts_at, ends_at),
    CONSTRAINT ck_office_assignments_type CHECK (office_type IN ('STUDENT_AFFAIRS_TEACHER', 'STUDENT_COUNCIL_PRESIDENT', 'STUDENT_COUNCIL_VICE_PRESIDENT')),
    CONSTRAINT ck_office_assignments_period CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT fk_office_assignments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_office_assignments_assigner FOREIGN KEY (assigned_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE activation_codes (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_by_user_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_activation_codes_user_active (user_id, expires_at, used_at, revoked_at),
    CONSTRAINT fk_activation_codes_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_activation_codes_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_tokens (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_by_user_id BINARY(16) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_password_reset_tokens_user_active (user_id, expires_at, used_at, revoked_at),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_password_reset_tokens_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE security_throttle_states (
    id BIGINT NOT NULL AUTO_INCREMENT,
    throttle_scope VARCHAR(32) NOT NULL,
    subject_fingerprint BINARY(32) NOT NULL,
    failure_count INT NOT NULL DEFAULT 0,
    blocked_until TIMESTAMP(6) NULL,
    last_failure_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_security_throttle_scope_subject (throttle_scope, subject_fingerprint),
    CONSTRAINT ck_security_throttle_failure_count CHECK (failure_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BINARY(16) NULL,
    event_type VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NULL,
    target_public_id BINARY(16) NULL,
    outcome VARCHAR(32) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    details_json JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_audit_logs_actor_created (actor_user_id, created_at),
    KEY ix_audit_logs_event_created (event_type, created_at),
    KEY ix_audit_logs_target_created (target_type, target_public_id, created_at),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100) NULL,
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BLOB NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;
