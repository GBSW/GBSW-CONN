package kr.hs.gbsw.communication.moderation.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ModerationNotFoundException extends ApiException {

    public ModerationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "MODERATION_NOT_FOUND", "신고 또는 심의 사건을 찾을 수 없습니다.");
    }
}
