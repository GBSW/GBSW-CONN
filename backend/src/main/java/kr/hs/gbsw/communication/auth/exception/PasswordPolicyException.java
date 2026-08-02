package kr.hs.gbsw.communication.auth.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PasswordPolicyException extends ApiException {

    public PasswordPolicyException(int minimumLength, int maximumLength) {
        super(
                HttpStatus.BAD_REQUEST,
                "PASSWORD_POLICY_VIOLATION",
                "비밀번호는 " + minimumLength + "자 이상 " + maximumLength + "자 이하로 입력해 주세요.");
    }
}
