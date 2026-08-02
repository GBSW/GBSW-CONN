package kr.hs.gbsw.communication.moderation.domain;

import java.util.UUID;
import kr.hs.gbsw.communication.office.domain.OfficeType;

public record ReviewerSnapshotCandidate(
        UUID userId,
        OfficeType office
) {
}
