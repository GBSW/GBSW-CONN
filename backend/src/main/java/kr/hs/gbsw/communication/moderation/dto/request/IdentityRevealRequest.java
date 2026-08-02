package kr.hs.gbsw.communication.moderation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IdentityRevealRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
