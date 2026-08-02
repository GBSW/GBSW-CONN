package kr.hs.gbsw.communication.proposal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.domain.ProposalViewRecord;

@Schema(description = "공개 제안 목록 항목")
public record ProposalSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID publicId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String excerpt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String authorVisibility,
        String authorDisplayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workflowStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String visibilityStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int supportCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int supportThreshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean viewerSupported,
        Instant formalizedAt,
        Integer formalizedSupportCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt
) {
    public static ProposalSummaryResponse from(ProposalViewRecord proposal, int supportThreshold) {
        return new ProposalSummaryResponse(
                proposal.publicId(), proposal.title(), proposal.content(),
                proposal.authorVisibility().name(), proposal.authorDisplayName(),
                proposal.workflowStatus().name(), proposal.visibilityStatus().name(),
                proposal.supportCount(), supportThreshold, proposal.viewerSupported(),
                proposal.formalizedAt(), proposal.formalizedSupportCount(), proposal.createdAt());
    }
}
