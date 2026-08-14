CREATE TABLE credential_delivery_records (
    id BINARY(16) NOT NULL,
    privileged_change_request_id BINARY(16) NOT NULL,
    target_user_id BINARY(16) NOT NULL,
    delivery_type VARCHAR(32) NOT NULL,
    delivery_status VARCHAR(16) NOT NULL,
    provider_message_id VARCHAR(255) NULL,
    attempted_at TIMESTAMP(6) NOT NULL,
    delivered_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_credential_delivery_request (privileged_change_request_id),
    KEY ix_credential_delivery_target (target_user_id, attempted_at),
    CONSTRAINT ck_credential_delivery_type CHECK (delivery_type IN ('ACTIVATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_credential_delivery_status CHECK (delivery_status IN ('DELIVERED')),
    CONSTRAINT fk_credential_delivery_request
        FOREIGN KEY (privileged_change_request_id) REFERENCES privileged_change_requests (id),
    CONSTRAINT fk_credential_delivery_target
        FOREIGN KEY (target_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
