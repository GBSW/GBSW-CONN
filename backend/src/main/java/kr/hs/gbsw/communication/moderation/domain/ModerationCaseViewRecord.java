package kr.hs.gbsw.communication.moderation.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.office.domain.OfficeType;
import kr.hs.gbsw.communication.proposal.domain.AuthorVisibility;

public record ModerationCaseViewRecord(
        UUID publicId,
        ModerationCaseType type,
        ModerationCaseStatus status,
        UUID proposalPublicId,
        String proposalTitle,
        String proposalContent,
        AuthorVisibility authorVisibility,
        String authorDisplayName,
        String sourceReportReason,
        String caseReason,
        Instant createdAt,
        Instant decidedAt,
        OfficeType viewerOffice,
        boolean viewerVoted,
        boolean identityRevealed,
        List<ModerationVoteRecord> votes
) {
}
