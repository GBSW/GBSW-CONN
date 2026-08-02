CREATE TABLE proposal_teacher_assignments (
    id BINARY(16) NOT NULL,
    proposal_id BINARY(16) NOT NULL,
    teacher_user_id BINARY(16) NOT NULL,
    assigned_by_user_id BINARY(16) NOT NULL,
    assignment_reason VARCHAR(500) NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL,
    unassigned_by_user_id BINARY(16) NULL,
    unassignment_reason VARCHAR(500) NULL,
    unassigned_at TIMESTAMP(6) NULL,
    current_proposal_id BINARY(16)
        GENERATED ALWAYS AS (IF(unassigned_at IS NULL, proposal_id, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_proposal_teacher_assignments_current (current_proposal_id),
    KEY ix_proposal_teacher_assignments_teacher (teacher_user_id, assigned_at),
    KEY ix_proposal_teacher_assignments_proposal (proposal_id, assigned_at),
    CONSTRAINT ck_proposal_teacher_assignments_end CHECK (
        (unassigned_by_user_id IS NULL AND unassignment_reason IS NULL AND unassigned_at IS NULL)
        OR (unassigned_by_user_id IS NOT NULL AND unassignment_reason IS NOT NULL AND unassigned_at IS NOT NULL)
    ),
    CONSTRAINT fk_proposal_teacher_assignments_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals (id),
    CONSTRAINT fk_proposal_teacher_assignments_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES users (id),
    CONSTRAINT fk_proposal_teacher_assignments_assigner
        FOREIGN KEY (assigned_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_proposal_teacher_assignments_unassigner
        FOREIGN KEY (unassigned_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE proposal_official_responses (
    id BINARY(16) NOT NULL,
    proposal_id BINARY(16) NOT NULL,
    responder_user_id BINARY(16) NOT NULL,
    resulting_status VARCHAR(32) NOT NULL,
    response_content TEXT NOT NULL,
    decision_reason TEXT NOT NULL,
    follow_up_plan TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_proposal_official_responses_proposal_created (proposal_id, created_at),
    CONSTRAINT ck_proposal_official_responses_status CHECK (
        resulting_status IN ('ACCEPTED', 'ON_HOLD', 'REJECTED', 'IN_PROGRESS', 'COMPLETED')
    ),
    CONSTRAINT fk_proposal_official_responses_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals (id),
    CONSTRAINT fk_proposal_official_responses_responder
        FOREIGN KEY (responder_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
