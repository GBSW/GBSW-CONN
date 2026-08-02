package kr.hs.gbsw.communication.proposal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.domain.ProposalOfficialResponseRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalStatusHistoryRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalViewRecord;

@Schema(description = "권한에 따라 공개되는 제안 상세")
public record ProposalDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID publicId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String authorVisibility,
        String authorDisplayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workflowStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String visibilityStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int supportCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int supportThreshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean viewerSupported,
        @Schema(description = "현재 조회자가 내부 지정된 활성 담당 교사인지 여부")
        boolean viewerCanManage,
        Instant formalizedAt,
        Integer formalizedSupportCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProposalStatusHistoryResponse> statusHistory,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProposalOfficialResponseResponse> officialResponses
) {
    public static ProposalDetailResponse from(
            ProposalViewRecord proposal,
            List<ProposalStatusHistoryRecord> history,
            int supportThreshold
    ) {
        return from(proposal, history, List.of(), false, supportThreshold);
    }

    public static ProposalDetailResponse from(
            ProposalViewRecord proposal,
            List<ProposalStatusHistoryRecord> history,
            List<ProposalOfficialResponseRecord> officialResponses,
            boolean viewerCanManage,
            int supportThreshold
    ) {
        return new ProposalDetailResponse(
                proposal.publicId(), proposal.title(), proposal.content(),
                proposal.authorVisibility().name(), proposal.authorDisplayName(),
                proposal.workflowStatus().name(), proposal.visibilityStatus().name(),
                proposal.supportCount(), supportThreshold, proposal.viewerSupported(), viewerCanManage,
                proposal.formalizedAt(), proposal.formalizedSupportCount(), proposal.createdAt(),
                history.stream().map(ProposalStatusHistoryResponse::from).toList(),
                officialResponses.stream().map(ProposalOfficialResponseResponse::from).toList());
    }
}
