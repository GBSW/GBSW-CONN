package kr.hs.gbsw.communication.proposal.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ProposalCommentNotFoundException extends ApiException {

    public ProposalCommentNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PROPOSAL_COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다.");
    }
}
