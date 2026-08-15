-- 신원 열람을 고정 심의자 세 명이 승인 후 일정 기간 동안 할 수 있게 바꾼다.
-- 기존 스키마는 사건당 한 건만 허용해 학생부장교사 한 명의 일회 열람만 가능했다.
-- 열람 가능 기간은 moderation_cases.decided_at 기준으로 서비스에서 판정하므로
-- 만료 시각을 따로 저장하지 않는다.
ALTER TABLE identity_reveal_records
    DROP INDEX uk_identity_reveal_records_case,
    ADD KEY ix_identity_reveal_records_case (case_id, revealed_at);
