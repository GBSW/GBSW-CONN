package kr.hs.gbsw.communication.proposal.domain;

import java.time.Instant;
import java.util.UUID;

public record AdminProposalRecord(
        UUID publicId,
        String title,
        ProposalWorkflowStatus workflowStatus,
        Instant formalizedAt,
        ProposalTeacherAssignmentRecord assignment
) {
}
