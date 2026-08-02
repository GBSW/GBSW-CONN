package kr.hs.gbsw.communication.proposal.domain;

import java.time.Instant;
import java.util.UUID;

public record CurrentProposalTeacherAssignment(
        UUID id,
        UUID teacherUserId,
        UUID teacherPublicId,
        String teacherDisplayName,
        Instant assignedAt
) {
    public ProposalTeacherAssignmentRecord toPublicRecord() {
        return new ProposalTeacherAssignmentRecord(teacherPublicId, teacherDisplayName, assignedAt);
    }
}
