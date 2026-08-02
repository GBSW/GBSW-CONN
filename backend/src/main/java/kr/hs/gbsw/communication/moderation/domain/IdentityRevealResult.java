package kr.hs.gbsw.communication.moderation.domain;

import java.time.Instant;

public record IdentityRevealResult(
        RevealedIdentity identity,
        Instant revealedAt
) {
}
