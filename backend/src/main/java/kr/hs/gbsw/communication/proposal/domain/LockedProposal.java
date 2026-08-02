package kr.hs.gbsw.communication.proposal.domain;

import java.util.UUID;

public record LockedProposal(
        UUID id,
        UUID publicId,
        ProposalWorkflowStatus workflowStatus,
        ProposalVisibilityStatus visibilityStatus
) {
}
