package kr.hs.gbsw.communication.proposal.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ProposalAssignmentRequiredException extends ApiException {

    public ProposalAssignmentRequiredException() {
        super(HttpStatus.FORBIDDEN, "PROPOSAL_ASSIGNMENT_REQUIRED", "이 정식 안건의 현재 담당 교사만 처리할 수 있습니다.");
    }
}
