package kr.hs.gbsw.communication.office.domain;

import java.time.Instant;
import java.util.UUID;

public record OfficeAssignmentRecord(
        UUID id,
        UUID userId,
        OfficeType office,
        Instant startsAt,
        Instant endsAt
) {
}
