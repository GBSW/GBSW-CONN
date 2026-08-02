package kr.hs.gbsw.communication.proposal.dto.response;

import java.util.UUID;
import kr.hs.gbsw.communication.proposal.domain.EligibleProposalTeacher;

public record EligibleProposalTeacherResponse(
        UUID publicId,
        String displayName
) {
    public static EligibleProposalTeacherResponse from(EligibleProposalTeacher teacher) {
        return new EligibleProposalTeacherResponse(teacher.publicId(), teacher.displayName());
    }
}
