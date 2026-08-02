package kr.hs.gbsw.communication.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "자체 계정 로그인 요청")
public record LoginRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{3,100}", message = "로그인 ID 형식을 확인해 주세요.")
        @Schema(example = "student.2026")
        String loginId,

        @NotBlank
        @Size(max = 256)
        @Schema(format = "password", example = "correct horse battery staple")
        String password
) {
}
