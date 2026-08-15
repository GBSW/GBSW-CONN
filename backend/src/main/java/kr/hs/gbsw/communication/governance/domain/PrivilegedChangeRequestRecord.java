package kr.hs.gbsw.communication.governance.domain;

import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.office.domain.OfficeType;
import kr.hs.gbsw.communication.user.domain.AccountRole;

public record PrivilegedChangeRequestRecord(
        UUID id,
        UUID publicId,
        PrivilegedChangeType type,
        PrivilegedChangeStatus status,
        UUID targetUserId,
        UUID targetUserPublicId,
        String loginId,
        String displayName,
        AccountRole role,
        OfficeType office,
        Instant startsAt,
        Instant endsAt,
        boolean replaceExistingAtStart,
        String reason,
        UUID requestedByUserId,
        UUID requestedByPublicId,
        String requestedByDisplayName,
        Instant requestedAt,
        Instant expiresAt,
        UUID approvedByPublicId,
        String approvedByDisplayName,
        Instant executedAt,
        String deliveryStatus
) {
}
