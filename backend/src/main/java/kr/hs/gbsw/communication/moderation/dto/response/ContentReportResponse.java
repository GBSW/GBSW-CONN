package kr.hs.gbsw.communication.moderation.dto.response;

import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.moderation.domain.ReportReceipt;

public record ContentReportResponse(
        UUID publicId,
        Instant createdAt
) {
    public static ContentReportResponse from(ReportReceipt receipt) {
        return new ContentReportResponse(receipt.publicId(), receipt.createdAt());
    }
}
