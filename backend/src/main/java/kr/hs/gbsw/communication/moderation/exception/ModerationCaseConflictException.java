package kr.hs.gbsw.communication.moderation.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ModerationCaseConflictException extends ApiException {

    public ModerationCaseConflictException(String message) {
        super(HttpStatus.CONFLICT, "MODERATION_CASE_CONFLICT", message);
    }
}
