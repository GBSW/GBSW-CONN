package kr.hs.gbsw.communication.proposal.dto.response;

import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.domain.AdminProposalRecord;

public record AdminProposalSummaryResponse(
        UUID publicId,
        String title,
        String workflowStatus,
        Instant formalizedAt,
        ProposalAssignmentResponse assignment
) {
    public static AdminProposalSummaryResponse from(AdminProposalRecord proposal) {
        return new AdminProposalSummaryResponse(
                proposal.publicId(), proposal.title(), proposal.workflowStatus().name(),
                proposal.formalizedAt(), ProposalAssignmentResponse.from(proposal.assignment()));
    }
}
