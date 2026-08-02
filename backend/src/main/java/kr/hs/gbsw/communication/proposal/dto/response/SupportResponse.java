package kr.hs.gbsw.communication.proposal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hs.gbsw.communication.proposal.domain.SupportResult;

@Schema(description = "동의 명령 이후 서버가 다시 계산한 상태")
public record SupportResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean supported,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int supportCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int supportThreshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workflowStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean justFormalized
) {
    public static SupportResponse from(SupportResult result, int supportThreshold) {
        return new SupportResponse(
                result.supported(), result.supportCount(), supportThreshold,
                result.workflowStatus().name(), result.justFormalized());
    }
}
