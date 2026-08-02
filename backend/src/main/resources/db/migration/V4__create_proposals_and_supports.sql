CREATE TABLE proposals (
    id BINARY(16) NOT NULL,
    public_id BINARY(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    author_visibility VARCHAR(16) NOT NULL,
    author_display_name VARCHAR(100) NULL,
    workflow_status VARCHAR(32) NOT NULL,
    visibility_status VARCHAR(32) NOT NULL,
    formalized_at TIMESTAMP(6) NULL,
    formalized_support_count INT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_proposals_public_id (public_id),
    KEY ix_proposals_feed (visibility_status, workflow_status, created_at),
    CONSTRAINT ck_proposals_author_visibility CHECK (author_visibility IN ('ANONYMOUS', 'NAMED')),
    CONSTRAINT ck_proposals_author_display CHECK (
        (author_visibility = 'ANONYMOUS' AND author_display_name IS NULL)
        OR (author_visibility = 'NAMED' AND author_display_name IS NOT NULL)
    ),
    CONSTRAINT ck_proposals_workflow_status CHECK (
        workflow_status IN (
            'GATHERING_SUPPORT', 'FORMAL_AGENDA', 'UNDER_REVIEW', 'ACCEPTED',
            'ON_HOLD', 'REJECTED', 'IN_PROGRESS', 'COMPLETED'
        )
    ),
    CONSTRAINT ck_proposals_visibility_status CHECK (
        visibility_status IN ('VISIBLE', 'RESTRICTED', 'HIDDEN_BY_DECISION')
    ),
    CONSTRAINT ck_proposals_formalization CHECK (
        (formalized_at IS NULL AND formalized_support_count IS NULL)
        OR (formalized_at IS NOT NULL AND formalized_support_count IS NOT NULL AND formalized_support_count > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE proposal_identities (
    proposal_id BINARY(16) NOT NULL,
    encrypted_user_id VARBINARY(128) NOT NULL,
    nonce BINARY(12) NOT NULL,
    key_version INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (proposal_id),
    CONSTRAINT ck_proposal_identities_key_version CHECK (key_version > 0),
    CONSTRAINT fk_proposal_identities_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE proposal_supports (
    proposal_id BINARY(16) NOT NULL,
    voter_user_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (proposal_id, voter_user_id),
    KEY ix_proposal_supports_voter_created (voter_user_id, created_at),
    CONSTRAINT fk_proposal_supports_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals (id),
    CONSTRAINT fk_proposal_supports_voter
        FOREIGN KEY (voter_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE proposal_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proposal_id BINARY(16) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    changed_by_user_id BINARY(16) NULL,
    support_count_snapshot INT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_proposal_status_history_proposal_created (proposal_id, created_at),
    CONSTRAINT ck_proposal_history_from_status CHECK (
        from_status IS NULL OR from_status IN (
            'GATHERING_SUPPORT', 'FORMAL_AGENDA', 'UNDER_REVIEW', 'ACCEPTED',
            'ON_HOLD', 'REJECTED', 'IN_PROGRESS', 'COMPLETED'
        )
    ),
    CONSTRAINT ck_proposal_history_to_status CHECK (
        to_status IN (
            'GATHERING_SUPPORT', 'FORMAL_AGENDA', 'UNDER_REVIEW', 'ACCEPTED',
            'ON_HOLD', 'REJECTED', 'IN_PROGRESS', 'COMPLETED'
        )
    ),
    CONSTRAINT fk_proposal_history_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals (id),
    CONSTRAINT fk_proposal_history_actor
        FOREIGN KEY (changed_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE proposal_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proposal_id BINARY(16) NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_proposal_notifications_type (proposal_id, notification_type),
    CONSTRAINT ck_proposal_notifications_type CHECK (
        notification_type IN ('FORMAL_AGENDA_CREATED')
    ),
    CONSTRAINT fk_proposal_notifications_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
