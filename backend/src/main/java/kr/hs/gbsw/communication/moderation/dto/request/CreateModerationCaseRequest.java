package kr.hs.gbsw.communication.moderation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseType;

public record CreateModerationCaseRequest(
        @NotNull ModerationCaseType caseType,
        @NotBlank @Size(max = 2000) String reason
) {
}
