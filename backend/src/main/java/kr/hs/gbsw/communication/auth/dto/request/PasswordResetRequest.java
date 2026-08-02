package kr.hs.gbsw.communication.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "일회용 코드로 비밀번호 재설정")
public record PasswordResetRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{3,100}", message = "로그인 ID 형식을 확인해 주세요.")
        @Schema(example = "student.2026")
        String loginId,

        @NotBlank
        @Pattern(regexp = "[23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{10,64}",
                message = "재설정 코드 형식을 확인해 주세요.")
        @Schema(description = "관리자에게 안전한 경로로 전달받은 일회용 코드", example = "G8vY4mN7qR2x")
        String resetCode,

        @NotBlank
        @Size(max = 256)
        @Schema(format = "password", example = "new correct horse battery staple")
        String newPassword
) {
}
