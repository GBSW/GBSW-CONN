package kr.hs.gbsw.communication.user.domain;

import java.time.Instant;
import java.util.UUID;

public record RoleAssignmentRecord(
        UUID id,
        AccountRole role,
        Instant startsAt,
        Instant endsAt
) {
}
