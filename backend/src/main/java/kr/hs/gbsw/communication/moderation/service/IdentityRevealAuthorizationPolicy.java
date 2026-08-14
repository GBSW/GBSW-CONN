package kr.hs.gbsw.communication.moderation.service;

import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import org.springframework.security.access.AccessDeniedException;

public final class IdentityRevealAuthorizationPolicy {

    private IdentityRevealAuthorizationPolicy() {
    }

    public static void requireAllowed(AuthPrincipal actor) {
        boolean prohibited = actor.authorities().contains("ROLE_SUPER_ADMIN")
                || actor.authorities().contains("OFFICE_STUDENT_COUNCIL_PRESIDENT")
                || actor.authorities().contains("OFFICE_STUDENT_COUNCIL_VICE_PRESIDENT");
        if (!actor.authorities().contains("ROLE_TEACHER") || prohibited) {
            throw new AccessDeniedException("Identity reveal requires an eligible teacher without prohibited authority");
        }
    }
}
