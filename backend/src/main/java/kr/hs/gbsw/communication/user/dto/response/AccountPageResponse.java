package kr.hs.gbsw.communication.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import kr.hs.gbsw.communication.user.domain.AccountSummaryRecord;

@Schema(description = "슈퍼 어드민용 계정 검색 결과")
public record AccountPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AccountSummaryResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages
) {
    public static AccountPageResponse from(
            List<AccountSummaryRecord> accounts,
            int page,
            int size,
            long totalElements
    ) {
        int totalPages = totalElements == 0 ? 0 : Math.toIntExact((totalElements + size - 1) / size);
        return new AccountPageResponse(
                accounts.stream().map(AccountSummaryResponse::from).toList(),
                page,
                size,
                totalElements,
                totalPages);
    }
}
