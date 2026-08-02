package kr.hs.gbsw.communication.proposal.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ProposalStateConflictException extends ApiException {

    public ProposalStateConflictException(String message) {
        super(HttpStatus.CONFLICT, "PROPOSAL_STATE_CONFLICT", message);
    }
}
