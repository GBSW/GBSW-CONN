package kr.hs.gbsw.communication.user.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.office.domain.OfficeType;

public record AccountSummaryRecord(
        UUID publicId,
        String loginId,
        String displayName,
        AccountStatus status,
        List<AccountRole> currentRoles,
        List<OfficeType> currentOffices,
        Instant createdAt
) {
}
