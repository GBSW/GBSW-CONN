package kr.hs.gbsw.communication.proposal.domain;

import java.time.Instant;
import java.util.UUID;

public record ProposalCommentRecord(
        UUID publicId,
        String authorDisplayName,
        String content,
        boolean viewerCanDelete,
        Instant createdAt
) {
}
