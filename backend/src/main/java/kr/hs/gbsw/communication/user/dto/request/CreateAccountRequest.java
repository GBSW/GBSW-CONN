package kr.hs.gbsw.communication.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.hs.gbsw.communication.user.domain.AccountRole;

@Schema(description = "활성화 대기 계정 생성")
public record CreateAccountRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{3,100}", message = "로그인 ID 형식을 확인해 주세요.")
        @Schema(example = "student.2026")
        String loginId,

        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]+", message = "표시 이름에 제어 문자를 사용할 수 없습니다.")
        @Schema(example = "홍길동")
        String displayName,

        @NotNull
        AccountRole role,

        @NotBlank
        @Size(max = 500)
        @Schema(example = "2026학년도 학생 계정 등록")
        String reason
) {
}
