package kr.hs.gbsw.communication.moderation.domain;

public record RevealedIdentity(
        String loginId,
        String displayName
) {
}
