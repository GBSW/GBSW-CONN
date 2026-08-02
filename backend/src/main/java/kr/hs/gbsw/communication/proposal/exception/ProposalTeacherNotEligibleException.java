package kr.hs.gbsw.communication.proposal.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ProposalTeacherNotEligibleException extends ApiException {

    public ProposalTeacherNotEligibleException() {
        super(HttpStatus.CONFLICT, "PROPOSAL_TEACHER_NOT_ELIGIBLE", "현재 활성 교사 역할이 있는 계정만 담당자로 지정할 수 있습니다.");
    }
}
