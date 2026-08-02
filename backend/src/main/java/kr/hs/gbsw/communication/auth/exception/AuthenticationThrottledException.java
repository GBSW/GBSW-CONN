package kr.hs.gbsw.communication.auth.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AuthenticationThrottledException extends ApiException {

    public AuthenticationThrottledException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "AUTHENTICATION_THROTTLED", "잠시 후 다시 시도해 주세요.");
    }
}
