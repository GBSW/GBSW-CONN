package kr.hs.gbsw.communication.office.domain;

import java.time.Instant;

public record OfficeAssignmentHistory(
        OfficeType office,
        Instant startsAt,
        Instant endsAt,
        Instant assignedAt,
        String reason,
        Instant endedAt,
        String endReason
) {
}
