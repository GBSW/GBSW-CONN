package kr.hs.gbsw.communication.user.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AccountConflictException extends ApiException {

    public AccountConflictException() {
        super(HttpStatus.CONFLICT, "ACCOUNT_CONFLICT", "이미 사용 중인 로그인 ID입니다.");
    }
}
