package kr.hs.gbsw.communication.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonRequest(
        @NotBlank
        @Size(max = 500)
        @Schema(example = "계정 도용 신고 확인")
        String reason
) {
}
