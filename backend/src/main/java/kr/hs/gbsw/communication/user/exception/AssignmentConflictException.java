package kr.hs.gbsw.communication.user.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AssignmentConflictException extends ApiException {

    public AssignmentConflictException(String safeMessage) {
        super(HttpStatus.CONFLICT, "ASSIGNMENT_CONFLICT", safeMessage);
    }
}
