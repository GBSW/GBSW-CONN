package kr.hs.gbsw.communication.auth.domain;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthPrincipal(
        UUID userId,
        UUID publicId,
        String loginId,
        String displayName,
        long credentialVersion,
        Instant reauthenticatedAt,
        List<String> authorities
) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    public AuthPrincipal {
        authorities = List.copyOf(authorities);
    }

    @Override
    public String getName() {
        return loginId;
    }
}
