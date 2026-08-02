package kr.hs.gbsw.communication.proposal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import kr.hs.gbsw.communication.proposal.domain.ProposalViewRecord;

@Schema(description = "접근 권한이 적용된 제안 피드")
public record ProposalPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProposalSummaryResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages
) {
    public static ProposalPageResponse from(
            List<ProposalViewRecord> proposals,
            int supportThreshold,
            int page,
            int size,
            long totalElements
    ) {
        int totalPages = totalElements == 0 ? 0 : Math.toIntExact((totalElements + size - 1) / size);
        return new ProposalPageResponse(
                proposals.stream().map(item -> ProposalSummaryResponse.from(item, supportThreshold)).toList(),
                page, size, totalElements, totalPages);
    }
}
