package kr.hs.gbsw.communication.office.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "임기 기반 보직 임명 또는 후임 예약")
public record OfficeAppointmentRequest(
        @NotNull UUID userPublicId,
        @Schema(description = "비우면 서버 현재 시각에 즉시 시작") Instant startsAt,
        Instant endsAt,
        @Schema(description = "시작 시점과 겹치는 현임자의 임기를 자동 종료") boolean replaceExistingAtStart,
        @NotBlank @Size(max = 500) String reason
) {
}
