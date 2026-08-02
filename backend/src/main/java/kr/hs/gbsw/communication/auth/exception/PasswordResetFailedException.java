package kr.hs.gbsw.communication.auth.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PasswordResetFailedException extends ApiException {

    public PasswordResetFailedException() {
        super(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_FAILED", "재설정 정보를 확인해 주세요.");
    }
}
