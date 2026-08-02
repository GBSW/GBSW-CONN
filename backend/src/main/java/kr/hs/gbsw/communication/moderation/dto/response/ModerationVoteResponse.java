package kr.hs.gbsw.communication.moderation.dto.response;

import java.time.Instant;
import kr.hs.gbsw.communication.moderation.domain.ModerationVoteRecord;

public record ModerationVoteResponse(
        String office,
        String decision,
        String reason,
        Instant createdAt
) {
    public static ModerationVoteResponse from(ModerationVoteRecord vote) {
        return new ModerationVoteResponse(
                vote.office().name(), vote.decision().name(), vote.reason(), vote.createdAt());
    }
}
