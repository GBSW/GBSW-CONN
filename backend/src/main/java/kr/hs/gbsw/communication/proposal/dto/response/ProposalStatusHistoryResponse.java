package kr.hs.gbsw.communication.proposal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.hs.gbsw.communication.proposal.domain.ProposalStatusHistoryRecord;

@Schema(description = "덮어쓰지 않는 제안 업무 상태 이력")
public record ProposalStatusHistoryResponse(
        String fromStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String toStatus,
        Integer supportCountSnapshot,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt
) {
    public static ProposalStatusHistoryResponse from(ProposalStatusHistoryRecord history) {
        return new ProposalStatusHistoryResponse(
                history.fromStatus() == null ? null : history.fromStatus().name(),
                history.toStatus().name(), history.supportCountSnapshot(),
                history.reason(), history.createdAt());
    }
}
