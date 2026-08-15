CREATE TABLE moderation_office_seats (
    office_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (office_type),
    CONSTRAINT ck_moderation_office_seat_type CHECK (
        office_type IN (
            'STUDENT_AFFAIRS_TEACHER',
            'STUDENT_COUNCIL_PRESIDENT',
            'STUDENT_COUNCIL_VICE_PRESIDENT'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO moderation_office_seats (office_type) VALUES
    ('STUDENT_AFFAIRS_TEACHER'),
    ('STUDENT_COUNCIL_PRESIDENT'),
    ('STUDENT_COUNCIL_VICE_PRESIDENT');
