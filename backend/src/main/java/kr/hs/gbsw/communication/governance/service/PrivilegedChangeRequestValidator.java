package kr.hs.gbsw.communication.governance.service;

import kr.hs.gbsw.communication.governance.dto.request.PrivilegedChangeCreateRequest;
import kr.hs.gbsw.communication.user.exception.AccountStateConflictException;

public final class PrivilegedChangeRequestValidator {

    private PrivilegedChangeRequestValidator() {
    }

    public static void validate(PrivilegedChangeCreateRequest request) {
        switch (request.changeType()) {
            case CREATE_ACCOUNT -> {
                require(request.targetUserPublicId() == null, "계정 생성 요청에는 기존 대상 계정을 지정할 수 없습니다.");
                require(hasText(request.loginId()) && hasText(request.displayName()) && request.role() != null,
                        "계정 생성에는 로그인 ID, 표시 이름과 첫 역할이 필요합니다.");
            }
            case REISSUE_ACTIVATION_CODE, ISSUE_PASSWORD_RESET_CODE ->
                    require(request.targetUserPublicId() != null, "코드 발급 대상 계정이 필요합니다.");
            case ASSIGN_ROLE, END_ROLE -> {
                require(request.targetUserPublicId() != null && request.role() != null,
                        "역할 변경에는 대상 계정과 역할이 필요합니다.");
            }
            case APPOINT_OFFICE, END_OFFICE -> {
                require(request.targetUserPublicId() != null && request.office() != null,
                        "보직 변경에는 대상 계정과 보직이 필요합니다.");
            }
        }
        if (request.startsAt() != null && request.endsAt() != null) {
            require(request.endsAt().isAfter(request.startsAt()), "종료 시각은 시작 시각보다 뒤여야 합니다.");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AccountStateConflictException(message);
        }
    }
}
