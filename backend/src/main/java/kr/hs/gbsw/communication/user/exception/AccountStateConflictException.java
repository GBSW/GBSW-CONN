package kr.hs.gbsw.communication.user.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AccountStateConflictException extends ApiException {

    public AccountStateConflictException(String safeMessage) {
        super(HttpStatus.CONFLICT, "ACCOUNT_STATE_CONFLICT", safeMessage);
    }
}
