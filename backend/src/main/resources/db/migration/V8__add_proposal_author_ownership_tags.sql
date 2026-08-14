CREATE TABLE proposal_author_ownership_tags (
    proposal_id BINARY(16) NOT NULL,
    lookup_key_version INT NOT NULL,
    owner_lookup_tag BINARY(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    rotated_at TIMESTAMP(6) NULL,
    PRIMARY KEY (proposal_id),
    CONSTRAINT ck_proposal_owner_tag_key_version CHECK (lookup_key_version > 0),
    CONSTRAINT fk_proposal_owner_tag_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
