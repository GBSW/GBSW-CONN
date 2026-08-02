package kr.hs.gbsw.communication.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AccountRecord;
import kr.hs.gbsw.communication.office.domain.OfficeAssignmentHistory;
import kr.hs.gbsw.communication.user.domain.RoleAssignmentHistory;

@Schema(description = "슈퍼 어드민에게 공개되는 계정 상태와 역할·보직 이력")
public record AccountDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID publicId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String loginId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RoleAssignmentHistoryResponse> roles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OfficeAssignmentHistoryResponse> offices
) {
    public static AccountDetailResponse from(
            AccountRecord account,
            List<RoleAssignmentHistory> roles,
            List<OfficeAssignmentHistory> offices
    ) {
        return new AccountDetailResponse(
                account.publicId(),
                account.loginId(),
                account.displayName(),
                account.status().name(),
                roles.stream().map(RoleAssignmentHistoryResponse::from).toList(),
                offices.stream().map(OfficeAssignmentHistoryResponse::from).toList());
    }
}
