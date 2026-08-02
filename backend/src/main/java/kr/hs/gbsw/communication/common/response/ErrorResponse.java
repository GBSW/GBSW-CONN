package kr.hs.gbsw.communication.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "공통 API 오류")
public record ErrorResponse(
        @Schema(example = "VALIDATION_FAILED", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(example = "요청 값을 확인해 주세요.", requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp,
        @Schema(description = "요청 상관관계 ID. 세션 또는 사용자 식별자가 아님",
                requiredMode = Schema.RequiredMode.REQUIRED) String traceId,
        List<FieldErrorResponse> fieldErrors
) {
    public static ErrorResponse of(String code, String message, Instant timestamp, String traceId) {
        return new ErrorResponse(code, message, timestamp, traceId, List.of());
    }
}
