package kr.hs.gbsw.communication.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.auth.exception.ReauthenticationRequiredException;
import kr.hs.gbsw.communication.auth.service.RecentAuthenticationGuard;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import org.junit.jupiter.api.Test;

class RecentAuthenticationGuardTest {

    private static final Instant NOW = Instant.parse("2026-08-02T03:00:00Z");
    private final RecentAuthenticationGuard guard = new RecentAuthenticationGuard(
            Clock.fixed(NOW, ZoneOffset.UTC),
            properties());

    @Test
    void acceptsAuthenticationAtTheConfiguredBoundary() {
        assertThatCode(() -> guard.requireRecent(principal(NOW.minus(Duration.ofMinutes(10)))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAuthenticationOlderThanTheConfiguredWindow() {
        assertThatThrownBy(() -> guard.requireRecent(principal(NOW.minus(Duration.ofMinutes(10).plusMillis(1)))))
                .isInstanceOf(ReauthenticationRequiredException.class);
    }

    private AuthPrincipal principal(Instant reauthenticatedAt) {
        return new AuthPrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "system.admin",
                "시스템 관리자",
                1,
                reauthenticatedAt,
                List.of("ROLE_SUPER_ADMIN"));
    }

    private ApplicationSecurityProperties properties() {
        return new ApplicationSecurityProperties(
                new ApplicationSecurityProperties.Cookie(false, "Lax"),
                new ApplicationSecurityProperties.RateLimit(5, 300, 120),
                new ApplicationSecurityProperties.Credentials(
                        Duration.ofHours(24),
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(10),
                        12,
                        128),
                new ApplicationSecurityProperties.Secrets(
                        "recent-authentication-guard-test-secret"));
    }
}
