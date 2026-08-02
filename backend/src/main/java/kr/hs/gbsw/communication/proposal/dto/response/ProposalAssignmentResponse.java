package kr.hs.gbsw.communication.proposal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.domain.ProposalTeacherAssignmentRecord;

@Schema(description = "슈퍼 어드민 전용 정식 안건 담당 교사 정보")
public record ProposalAssignmentResponse(
        UUID teacherPublicId,
        String teacherDisplayName,
        Instant assignedAt
) {
    public static ProposalAssignmentResponse from(ProposalTeacherAssignmentRecord assignment) {
        return assignment == null ? null : new ProposalAssignmentResponse(
                assignment.teacherPublicId(), assignment.teacherDisplayName(), assignment.assignedAt());
    }
}
