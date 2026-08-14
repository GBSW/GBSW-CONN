package kr.hs.gbsw.communication.moderation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.moderation.service.IdentityRevealAuthorizationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class IdentityRevealAuthorizationPolicyTest {

    @Test
    void rejectsSuperAdminAndCurrentStudentOfficer() {
        assertThrows(AccessDeniedException.class, () -> IdentityRevealAuthorizationPolicy.requireAllowed(
                principal(List.of("ROLE_TEACHER", "ROLE_SUPER_ADMIN"))));
        assertThrows(AccessDeniedException.class, () -> IdentityRevealAuthorizationPolicy.requireAllowed(
                principal(List.of("ROLE_TEACHER", "OFFICE_STUDENT_COUNCIL_PRESIDENT"))));
    }

    @Test
    void acceptsTeacherWithoutProhibitedAuthority() {
        assertDoesNotThrow(() -> IdentityRevealAuthorizationPolicy.requireAllowed(
                principal(List.of("ROLE_TEACHER"))));
    }

    private AuthPrincipal principal(List<String> authorities) {
        return new AuthPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "teacher", "교사", 1,
                Instant.parse("2026-01-01T00:00:00Z"), authorities);
    }
}
