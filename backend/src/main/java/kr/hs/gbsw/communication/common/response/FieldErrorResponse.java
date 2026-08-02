package kr.hs.gbsw.communication.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "입력 필드 검증 오류")
public record FieldErrorResponse(
        @Schema(example = "title") String field,
        @Schema(example = "제목을 입력해 주세요.") String reason
) {
}
