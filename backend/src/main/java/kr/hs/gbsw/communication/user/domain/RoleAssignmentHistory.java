package kr.hs.gbsw.communication.user.domain;

import java.time.Instant;

public record RoleAssignmentHistory(
        AccountRole role,
        Instant startsAt,
        Instant endsAt,
        Instant assignedAt,
        String reason,
        Instant endedAt,
        String endReason
) {
}
