ALTER TABLE privileged_change_requests
    DROP CHECK ck_privileged_change_distinct_approver,
    ADD CONSTRAINT ck_privileged_change_distinct_approver CHECK (
        (
            bootstrap_quorum_exception = FALSE
            AND (approved_by_user_id IS NULL OR approved_by_user_id <> requested_by_user_id)
        )
        OR (
            bootstrap_quorum_exception = TRUE
            AND approved_by_user_id = requested_by_user_id
            AND (
                (change_type = 'CREATE_ACCOUNT' AND role_type = 'SUPER_ADMIN')
                OR (change_type = 'REISSUE_ACTIVATION_CODE' AND target_user_id IS NOT NULL)
            )
        )
    );
