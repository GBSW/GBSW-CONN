package kr.hs.gbsw.communication.governance.dto.response;

import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.governance.domain.PrivilegedChangeRequestRecord;

public record PrivilegedChangeResponse(
        UUID publicId,
        String changeType,
        String status,
        UUID targetUserPublicId,
        String requestedByDisplayName,
        Instant requestedAt,
        Instant expiresAt,
        String approvedByDisplayName,
        Instant executedAt,
        String deliveryStatus
) {
    public static PrivilegedChangeResponse from(PrivilegedChangeRequestRecord record, Instant now) {
        String effectiveStatus = record.status().name();
        if (record.status().name().equals("PENDING") && !now.isBefore(record.expiresAt())) {
            effectiveStatus = "EXPIRED";
        }
        return new PrivilegedChangeResponse(
                record.publicId(), record.type().name(), effectiveStatus, record.targetUserPublicId(),
                record.requestedByDisplayName(), record.requestedAt(), record.expiresAt(),
                record.approvedByDisplayName(), record.executedAt(), record.deliveryStatus());
    }
}
