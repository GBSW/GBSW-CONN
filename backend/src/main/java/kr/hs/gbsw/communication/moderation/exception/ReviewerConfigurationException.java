package kr.hs.gbsw.communication.moderation.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ReviewerConfigurationException extends ApiException {

    public ReviewerConfigurationException() {
        super(HttpStatus.CONFLICT, "REVIEWER_CONFIGURATION_INVALID",
                "학생부장·학생회장·학생부회장 현임자가 정확히 한 명씩 있어야 사건을 만들 수 있습니다.");
    }
}
