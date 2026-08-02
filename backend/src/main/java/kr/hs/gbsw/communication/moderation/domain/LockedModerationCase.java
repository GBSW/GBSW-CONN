package kr.hs.gbsw.communication.moderation.domain;

import java.util.UUID;
import kr.hs.gbsw.communication.office.domain.OfficeType;

public record LockedModerationCase(
        UUID id,
        UUID publicId,
        UUID proposalId,
        UUID proposalPublicId,
        ModerationCaseType type,
        ModerationCaseStatus status,
        UUID reviewerSnapshotId,
        OfficeType viewerOffice
) {
}
