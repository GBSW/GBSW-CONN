CREATE TABLE content_reports (
    id BINARY(16) NOT NULL,
    public_id BINARY(16) NOT NULL,
    proposal_id BINARY(16) NOT NULL,
    reporter_user_id BINARY(16) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_content_reports_public_id (public_id),
    UNIQUE KEY uk_content_reports_proposal_reporter (proposal_id, reporter_user_id),
    KEY ix_content_reports_created (created_at),
    CONSTRAINT fk_content_reports_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals (id),
    CONSTRAINT fk_content_reports_reporter
        FOREIGN KEY (reporter_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE moderation_cases (
    id BINARY(16) NOT NULL,
    public_id BINARY(16) NOT NULL,
    proposal_id BINARY(16) NOT NULL,
    source_report_id BINARY(16) NOT NULL,
    case_type VARCHAR(32) NOT NULL,
    case_status VARCHAR(16) NOT NULL,
    created_by_user_id BINARY(16) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    decided_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_moderation_cases_public_id (public_id),
    UNIQUE KEY uk_moderation_cases_proposal_type (proposal_id, case_type),
    KEY ix_moderation_cases_status_created (case_status, created_at),
    CONSTRAINT ck_moderation_cases_type CHECK (
        case_type IN ('CONTENT_VISIBILITY', 'IDENTITY_REVEAL')
    ),
    CONSTRAINT ck_moderation_cases_status CHECK (
        case_status IN ('PENDING', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_moderation_cases_decision CHECK (
        (case_status = 'PENDING' AND decided_at IS NULL)
        OR (case_status IN ('APPROVED', 'REJECTED') AND decided_at IS NOT NULL)
    ),
    CONSTRAINT fk_moderation_cases_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals (id),
    CONSTRAINT fk_moderation_cases_source_report
        FOREIGN KEY (source_report_id) REFERENCES content_reports (id),
    CONSTRAINT fk_moderation_cases_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE moderation_reviewer_snapshots (
    id BINARY(16) NOT NULL,
    case_id BINARY(16) NOT NULL,
    reviewer_user_id BINARY(16) NOT NULL,
    office_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_moderation_reviewer_case_office (case_id, office_type),
    UNIQUE KEY uk_moderation_reviewer_case_user (case_id, reviewer_user_id),
    KEY ix_moderation_reviewer_user (reviewer_user_id, created_at),
    CONSTRAINT ck_moderation_reviewer_office CHECK (
        office_type IN (
            'STUDENT_AFFAIRS_TEACHER',
            'STUDENT_COUNCIL_PRESIDENT',
            'STUDENT_COUNCIL_VICE_PRESIDENT'
        )
    ),
    CONSTRAINT fk_moderation_reviewer_case
        FOREIGN KEY (case_id) REFERENCES moderation_cases (id),
    CONSTRAINT fk_moderation_reviewer_user
        FOREIGN KEY (reviewer_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE moderation_votes (
    id BINARY(16) NOT NULL,
    reviewer_snapshot_id BINARY(16) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_moderation_votes_reviewer (reviewer_snapshot_id),
    CONSTRAINT ck_moderation_votes_decision CHECK (decision IN ('APPROVE', 'REJECT')),
    CONSTRAINT fk_moderation_votes_reviewer
        FOREIGN KEY (reviewer_snapshot_id) REFERENCES moderation_reviewer_snapshots (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE proposal_visibility_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proposal_id BINARY(16) NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    moderation_case_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_proposal_visibility_history_case (moderation_case_id),
    KEY ix_proposal_visibility_history_proposal (proposal_id, created_at),
    CONSTRAINT ck_proposal_visibility_history_from CHECK (
        from_status IN ('VISIBLE', 'RESTRICTED', 'HIDDEN_BY_DECISION')
    ),
    CONSTRAINT ck_proposal_visibility_history_to CHECK (
        to_status IN ('VISIBLE', 'RESTRICTED', 'HIDDEN_BY_DECISION')
    ),
    CONSTRAINT fk_proposal_visibility_history_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals (id),
    CONSTRAINT fk_proposal_visibility_history_case
        FOREIGN KEY (moderation_case_id) REFERENCES moderation_cases (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE identity_reveal_records (
    id BINARY(16) NOT NULL,
    case_id BINARY(16) NOT NULL,
    revealed_by_user_id BINARY(16) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    revealed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_identity_reveal_records_case (case_id),
    KEY ix_identity_reveal_records_actor (revealed_by_user_id, revealed_at),
    CONSTRAINT fk_identity_reveal_records_case
        FOREIGN KEY (case_id) REFERENCES moderation_cases (id),
    CONSTRAINT fk_identity_reveal_records_actor
        FOREIGN KEY (revealed_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE security_throttle_states
    DROP CHECK ck_security_throttle_scope,
    ADD CONSTRAINT ck_security_throttle_scope CHECK (
        throttle_scope IN (
            'LOGIN_ACCOUNT', 'LOGIN_IP',
            'ACTIVATION_ACCOUNT', 'ACTIVATION_IP',
            'PASSWORD_RESET_ACCOUNT', 'PASSWORD_RESET_IP',
            'REAUTHENTICATION_ACCOUNT', 'REAUTHENTICATION_IP',
            'IDENTITY_REVEAL_ACCOUNT', 'IDENTITY_REVEAL_IP'
        )
    );
