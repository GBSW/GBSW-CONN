package kr.hs.gbsw.communication.proposal.dto.response;

import kr.hs.gbsw.communication.proposal.domain.ProposalWorkflowStatus;

public record ProposalWorkflowResponse(String workflowStatus) {
    public static ProposalWorkflowResponse from(ProposalWorkflowStatus status) {
        return new ProposalWorkflowResponse(status.name());
    }
}
