package kr.hs.gbsw.communication.moderation.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContentReportRecord(
        UUID publicId,
        UUID proposalPublicId,
        String proposalTitle,
        String proposalContent,
        String reason,
        Instant createdAt,
        List<ModerationCaseType> caseTypes
) {
}
