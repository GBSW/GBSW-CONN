package kr.hs.gbsw.communication.auth.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ActivationFailedException extends ApiException {

    public ActivationFailedException() {
        super(HttpStatus.BAD_REQUEST, "ACTIVATION_FAILED", "가입 정보를 확인해 주세요.");
    }
}
