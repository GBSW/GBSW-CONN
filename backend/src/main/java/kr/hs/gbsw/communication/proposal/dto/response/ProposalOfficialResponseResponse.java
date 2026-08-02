package kr.hs.gbsw.communication.proposal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.hs.gbsw.communication.proposal.domain.ProposalOfficialResponseRecord;

@Schema(description = "학생과 교사에게 공개되는 학교 공식 답변. 응답 교사의 식별 정보는 포함하지 않습니다.")
public record ProposalOfficialResponseResponse(
        String resultingStatus,
        String content,
        String decisionReason,
        String followUpPlan,
        Instant createdAt
) {
    public static ProposalOfficialResponseResponse from(ProposalOfficialResponseRecord response) {
        return new ProposalOfficialResponseResponse(
                response.resultingStatus().name(),
                response.content(),
                response.decisionReason(),
                response.followUpPlan(),
                response.createdAt());
    }
}
