package kr.hs.gbsw.communication.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "민감 작업 전 최근 재인증 요청")
public record ReauthenticationRequest(
        @NotBlank
        @Size(max = 256)
        @Schema(format = "password", example = "correct horse battery staple")
        String password
) {
}
