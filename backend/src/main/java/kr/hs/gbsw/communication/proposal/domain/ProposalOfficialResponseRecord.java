package kr.hs.gbsw.communication.proposal.domain;

import java.time.Instant;

public record ProposalOfficialResponseRecord(
        ProposalWorkflowStatus resultingStatus,
        String content,
        String decisionReason,
        String followUpPlan,
        Instant createdAt
) {
}
