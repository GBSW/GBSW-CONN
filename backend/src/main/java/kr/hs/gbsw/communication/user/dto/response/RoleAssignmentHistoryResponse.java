package kr.hs.gbsw.communication.user.dto.response;

import java.time.Instant;
import kr.hs.gbsw.communication.user.domain.RoleAssignmentHistory;

public record RoleAssignmentHistoryResponse(
        String role,
        Instant startsAt,
        Instant endsAt,
        Instant assignedAt,
        String reason,
        Instant endedAt,
        String endReason
) {
    public static RoleAssignmentHistoryResponse from(RoleAssignmentHistory history) {
        return new RoleAssignmentHistoryResponse(
                history.role().name(), history.startsAt(), history.endsAt(), history.assignedAt(),
                history.reason(), history.endedAt(), history.endReason());
    }
}
