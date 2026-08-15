package kr.hs.gbsw.communication.moderation.service;

import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import org.springframework.security.access.AccessDeniedException;

public final class IdentityRevealAuthorizationPolicy {

    private IdentityRevealAuthorizationPolicy() {
    }

    public static void requireAllowed(AuthPrincipal actor) {
        boolean supportedRole = actor.authorities().contains("ROLE_STUDENT")
                || actor.authorities().contains("ROLE_TEACHER");
        if (actor.authorities().contains("ROLE_SUPER_ADMIN") || !supportedRole) {
            throw new AccessDeniedException("Identity reveal requires an assigned student or teacher reviewer");
        }
    }
}
