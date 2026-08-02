package kr.hs.gbsw.communication.auth.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ReauthenticationRequiredException extends ApiException {

    public ReauthenticationRequiredException() {
        super(
                HttpStatus.FORBIDDEN,
                "REAUTHENTICATION_REQUIRED",
                "보안을 위해 비밀번호를 다시 확인해 주세요.");
    }
}
