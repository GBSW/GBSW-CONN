package kr.hs.gbsw.communication.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.hs.gbsw.communication.user.domain.AssignmentPeriod;

@Schema(description = "저장된 역할 또는 보직 임기")
public record AssignmentPeriodResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
        Instant endsAt
) {
    public static AssignmentPeriodResponse from(AssignmentPeriod period) {
        return new AssignmentPeriodResponse(period.type(), period.startsAt(), period.endsAt());
    }
}
