package kr.hs.gbsw.communication.moderation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModerationVoteRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
