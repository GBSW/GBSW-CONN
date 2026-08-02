package kr.hs.gbsw.communication.proposal.domain;

public record SupportResult(
        boolean supported,
        int supportCount,
        ProposalWorkflowStatus workflowStatus,
        boolean justFormalized
) {
}
