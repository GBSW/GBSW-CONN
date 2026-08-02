package kr.hs.gbsw.communication.user.dto.response;

import java.time.Instant;
import kr.hs.gbsw.communication.office.domain.OfficeAssignmentHistory;

public record OfficeAssignmentHistoryResponse(
        String office,
        Instant startsAt,
        Instant endsAt,
        Instant assignedAt,
        String reason,
        Instant endedAt,
        String endReason
) {
    public static OfficeAssignmentHistoryResponse from(OfficeAssignmentHistory history) {
        return new OfficeAssignmentHistoryResponse(
                history.office().name(), history.startsAt(), history.endsAt(), history.assignedAt(),
                history.reason(), history.endedAt(), history.endReason());
    }
}
