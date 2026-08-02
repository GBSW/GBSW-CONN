package kr.hs.gbsw.communication.auth.domain;

import java.util.UUID;
import kr.hs.gbsw.communication.user.domain.AccountStatus;

public record AccountRecord(
        UUID id,
        UUID publicId,
        String loginId,
        String displayName,
        AccountStatus status,
        String passwordHash,
        Long credentialVersion
) {
    public boolean hasCredentials() {
        return passwordHash != null && credentialVersion != null;
    }
}
