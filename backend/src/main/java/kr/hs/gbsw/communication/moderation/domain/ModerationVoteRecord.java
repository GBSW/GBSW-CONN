package kr.hs.gbsw.communication.moderation.domain;

import java.time.Instant;
import kr.hs.gbsw.communication.office.domain.OfficeType;

public record ModerationVoteRecord(
        OfficeType office,
        ModerationVoteDecision decision,
        String reason,
        Instant createdAt
) {
}
