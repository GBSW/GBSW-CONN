package kr.hs.gbsw.communication.user.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidAssignmentPeriodException extends ApiException {

    public InvalidAssignmentPeriodException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNMENT_PERIOD", "임명 시작·종료 시각을 확인해 주세요.");
    }
}
