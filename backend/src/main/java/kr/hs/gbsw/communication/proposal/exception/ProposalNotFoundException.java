package kr.hs.gbsw.communication.proposal.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ProposalNotFoundException extends ApiException {

    public ProposalNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PROPOSAL_NOT_FOUND", "제안을 찾을 수 없습니다.");
    }
}
