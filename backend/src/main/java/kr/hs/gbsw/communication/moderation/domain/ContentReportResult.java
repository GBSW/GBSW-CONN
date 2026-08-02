package kr.hs.gbsw.communication.moderation.domain;

public record ContentReportResult(
        ReportReceipt receipt,
        boolean created
) {
}
