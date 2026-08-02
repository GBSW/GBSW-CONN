package kr.hs.gbsw.communication.moderation.domain;

import java.util.UUID;

public record LockedContentReport(
        UUID id,
        UUID proposalId,
        UUID proposalPublicId,
        String reason
) {
}
