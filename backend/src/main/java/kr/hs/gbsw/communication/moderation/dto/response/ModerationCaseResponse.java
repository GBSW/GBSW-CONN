package kr.hs.gbsw.communication.moderation.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseViewRecord;

public record ModerationCaseResponse(
        UUID publicId,
        String caseType,
        String caseStatus,
        UUID proposalPublicId,
        String proposalTitle,
        String proposalContent,
        String authorVisibility,
        String authorDisplayName,
        String sourceReportReason,
        String caseReason,
        Instant createdAt,
        Instant decidedAt,
        String viewerOffice,
        boolean viewerVoted,
        boolean identityRevealed,
        List<ModerationVoteResponse> votes
) {
    public static ModerationCaseResponse from(ModerationCaseViewRecord caseRecord) {
        return new ModerationCaseResponse(
                caseRecord.publicId(), caseRecord.type().name(), caseRecord.status().name(),
                caseRecord.proposalPublicId(), caseRecord.proposalTitle(), caseRecord.proposalContent(),
                caseRecord.authorVisibility().name(), caseRecord.authorDisplayName(),
                caseRecord.sourceReportReason(), caseRecord.caseReason(),
                caseRecord.createdAt(), caseRecord.decidedAt(), caseRecord.viewerOffice().name(),
                caseRecord.viewerVoted(), caseRecord.identityRevealed(),
                caseRecord.votes().stream().map(ModerationVoteResponse::from).toList());
    }
}
