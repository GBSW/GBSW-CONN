package kr.hs.gbsw.communication.moderation.dto.response;

import java.time.Instant;
import kr.hs.gbsw.communication.moderation.domain.IdentityRevealResult;

public record IdentityRevealResponse(
        String loginId,
        String displayName,
        Instant revealedAt
) {
    public static IdentityRevealResponse from(IdentityRevealResult result) {
        return new IdentityRevealResponse(
                result.identity().loginId(), result.identity().displayName(), result.revealedAt());
    }
}
