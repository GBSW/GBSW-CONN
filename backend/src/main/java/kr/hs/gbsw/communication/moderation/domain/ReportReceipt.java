package kr.hs.gbsw.communication.moderation.domain;

import java.time.Instant;
import java.util.UUID;

public record ReportReceipt(UUID publicId, Instant createdAt) {
}
