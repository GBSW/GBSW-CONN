package kr.hs.gbsw.communication.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "API 실행 상태")
public record SystemStatusResponse(
        @Schema(example = "ok") String status,
        Instant serverTime,
        @Schema(example = "v1") String apiVersion
) {
}
