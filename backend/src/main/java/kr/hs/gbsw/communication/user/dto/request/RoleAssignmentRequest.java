package kr.hs.gbsw.communication.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import kr.hs.gbsw.communication.user.domain.AccountRole;

@Schema(description = "기본 계정 역할 임기 추가")
public record RoleAssignmentRequest(
        @NotNull AccountRole role,
        @Schema(description = "비우면 서버 현재 시각에 즉시 시작") Instant startsAt,
        Instant endsAt,
        @NotBlank @Size(max = 500) String reason
) {
}
