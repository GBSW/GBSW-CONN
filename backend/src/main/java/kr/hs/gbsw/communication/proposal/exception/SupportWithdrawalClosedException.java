package kr.hs.gbsw.communication.proposal.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class SupportWithdrawalClosedException extends ApiException {

    public SupportWithdrawalClosedException() {
        super(HttpStatus.CONFLICT, "SUPPORT_WITHDRAWAL_CLOSED", "정식 안건으로 승격된 뒤에는 동의를 철회할 수 없습니다.");
    }
}
