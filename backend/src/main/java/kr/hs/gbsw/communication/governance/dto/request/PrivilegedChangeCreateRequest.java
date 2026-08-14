package kr.hs.gbsw.communication.governance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.governance.domain.PrivilegedChangeType;
import kr.hs.gbsw.communication.office.domain.OfficeType;
import kr.hs.gbsw.communication.user.domain.AccountRole;

public record PrivilegedChangeCreateRequest(
        @NotNull PrivilegedChangeType changeType,
        UUID targetUserPublicId,
        @Size(max = 100) String loginId,
        @Size(max = 100) String displayName,
        AccountRole role,
        OfficeType office,
        Instant startsAt,
        Instant endsAt,
        Boolean replaceExistingAtStart,
        @NotBlank @Size(max = 2000) String reason
) {
    public boolean replaceExisting() {
        return Boolean.TRUE.equals(replaceExistingAtStart);
    }
}
