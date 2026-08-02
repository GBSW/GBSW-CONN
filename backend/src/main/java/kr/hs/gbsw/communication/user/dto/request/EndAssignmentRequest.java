package kr.hs.gbsw.communication.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Schema(description = "역할 또는 보직 임기 종료")
public record EndAssignmentRequest(
        @Schema(description = "비우면 서버 현재 시각에 즉시 종료") Instant endsAt,
        @NotBlank @Size(max = 500) String reason
) {
}
