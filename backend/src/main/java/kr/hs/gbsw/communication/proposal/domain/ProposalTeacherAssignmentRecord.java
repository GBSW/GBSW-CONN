package kr.hs.gbsw.communication.proposal.domain;

import java.time.Instant;
import java.util.UUID;

public record ProposalTeacherAssignmentRecord(
        UUID teacherPublicId,
        String teacherDisplayName,
        Instant assignedAt
) {
}
