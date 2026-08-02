package kr.hs.gbsw.communication.proposal.domain;

import java.time.Instant;

public record ProposalStatusHistoryRecord(
        ProposalWorkflowStatus fromStatus,
        ProposalWorkflowStatus toStatus,
        Integer supportCountSnapshot,
        String reason,
        Instant createdAt
) {
}
