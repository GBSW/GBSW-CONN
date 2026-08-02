ALTER TABLE proposals
    ADD COLUMN withdrawn_at TIMESTAMP(6) NULL AFTER formalized_support_count,
    ADD KEY ix_proposals_withdrawn_at (withdrawn_at);

CREATE TABLE proposal_comments (
    id BINARY(16) NOT NULL,
    public_id BINARY(16) NOT NULL,
    proposal_id BINARY(16) NOT NULL,
    author_user_id BINARY(16) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_proposal_comments_public_id (public_id),
    KEY ix_proposal_comments_proposal_created (proposal_id, created_at),
    KEY ix_proposal_comments_author (author_user_id),
    CONSTRAINT fk_proposal_comments_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals (id),
    CONSTRAINT fk_proposal_comments_author
        FOREIGN KEY (author_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
