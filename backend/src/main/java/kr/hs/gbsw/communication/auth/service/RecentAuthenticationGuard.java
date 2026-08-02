package kr.hs.gbsw.communication.auth.service;

import java.time.Clock;
import java.time.Instant;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.auth.exception.ReauthenticationRequiredException;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import org.springframework.stereotype.Component;

@Component
public class RecentAuthenticationGuard {

    private final Clock clock;
    private final ApplicationSecurityProperties properties;

    public RecentAuthenticationGuard(Clock clock, ApplicationSecurityProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public void requireRecent(AuthPrincipal principal) {
        Instant validAfter = clock.instant().minus(properties.credentials().reauthenticationTtl());
        if (principal.reauthenticatedAt().isBefore(validAfter)) {
            throw new ReauthenticationRequiredException();
        }
    }
}
