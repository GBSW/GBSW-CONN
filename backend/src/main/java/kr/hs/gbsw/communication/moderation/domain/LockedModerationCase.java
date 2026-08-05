package kr.hs.gbsw.communication.moderation.domain;

import java.time.Instant;
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
        OfficeType viewerOffice,
        /** 승인 또는 반려가 확정된 시각. 신원 열람 가능 기간의 기준이다. */
        Instant decidedAt
) {
}
