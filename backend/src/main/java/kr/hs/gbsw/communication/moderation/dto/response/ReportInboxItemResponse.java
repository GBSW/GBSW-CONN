package kr.hs.gbsw.communication.moderation.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.moderation.domain.ContentReportRecord;

public record ReportInboxItemResponse(
        UUID publicId,
        UUID proposalPublicId,
        String proposalTitle,
        String proposalContent,
        String reason,
        Instant createdAt,
        List<String> existingCaseTypes
) {
    public static ReportInboxItemResponse from(ContentReportRecord report) {
        return new ReportInboxItemResponse(
                report.publicId(), report.proposalPublicId(), report.proposalTitle(), report.proposalContent(),
                report.reason(), report.createdAt(),
                report.caseTypes().stream().map(Enum::name).toList());
    }
}
