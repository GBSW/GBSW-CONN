package kr.hs.gbsw.communication.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "최초 계정 활성화 요청")
public record ActivationRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{3,100}", message = "로그인 ID 형식을 확인해 주세요.")
        @Schema(example = "student.2026")
        String loginId,

        @NotBlank
        @Pattern(regexp = "[23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{10,64}",
                message = "가입 코드 형식을 확인해 주세요.")
        @Schema(description = "관리자에게 안전한 경로로 전달받은 일회용 코드", example = "7Kp9zT4Wh3Nm")
        String activationCode,

        @NotBlank
        @Size(max = 256)
        @Schema(format = "password", example = "correct horse battery staple")
        String password
) {
}
