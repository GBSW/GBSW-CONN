package kr.hs.gbsw.communication.moderation.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class IdentityRevealUnavailableException extends ApiException {

    public IdentityRevealUnavailableException(String message) {
        super(HttpStatus.CONFLICT, "IDENTITY_REVEAL_UNAVAILABLE", message);
    }
}
