package kr.hs.gbsw.communication.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.user.domain.AccountSummaryRecord;

@Schema(description = "계정 목록에 표시되는 현재 상태 요약")
public record AccountSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID publicId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String loginId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> currentRoles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> currentOffices,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt
) {
    public static AccountSummaryResponse from(AccountSummaryRecord account) {
        return new AccountSummaryResponse(
                account.publicId(),
                account.loginId(),
                account.displayName(),
                account.status().name(),
                account.currentRoles().stream().map(Enum::name).toList(),
                account.currentOffices().stream().map(Enum::name).toList(),
                account.createdAt());
    }
}
