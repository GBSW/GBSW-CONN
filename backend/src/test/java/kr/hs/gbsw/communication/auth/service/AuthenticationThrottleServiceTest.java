package kr.hs.gbsw.communication.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.ThrottleOperation;
import kr.hs.gbsw.communication.auth.domain.ThrottleState;
import kr.hs.gbsw.communication.auth.repository.ThrottleRepository;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import kr.hs.gbsw.communication.common.config.DeploymentSecurityProperties;
import org.junit.jupiter.api.Test;

class AuthenticationThrottleServiceTest {

    @Test
    void unknownAccountsStayInBoundedMemoryAndNeverCreatePersistentRows() {
        ThrottleRepository repository = mock(ThrottleRepository.class);
        AuthenticationThrottleService service = service(repository, 100);
        Instant now = Instant.parse("2026-08-14T00:00:00Z");

        for (int index = 0; index < 150; index++) {
            service.recordFailure(
                    ThrottleOperation.LOGIN,
                    "unknown-" + index,
                    "203.0.113." + index,
                    now);
        }

        assertThat(service.memoryStateCount()).isLessThanOrEqualTo(100);
        verifyNoInteractions(repository);
    }

    @Test
    void realAccountUsesExpiringPersistentState() {
        ThrottleRepository repository = mock(ThrottleRepository.class);
        AuthenticationThrottleService service = service(repository, 100);
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        when(repository.lockOrCreate(
                eq(ThrottleOperation.LOGIN.accountScope()),
                eq(accountId),
                eq(now),
                any(Instant.class)))
                .thenReturn(new ThrottleState(0, null));

        service.assertAllowed(
                ThrottleOperation.LOGIN, accountId, "student", "203.0.113.8", now);

        verify(repository).lockOrCreate(
                ThrottleOperation.LOGIN.accountScope(),
                accountId,
                now,
                now.plus(Duration.ofHours(24)));
    }

    private AuthenticationThrottleService service(ThrottleRepository repository, int memoryEntries) {
        ApplicationSecurityProperties security = new ApplicationSecurityProperties(
                new ApplicationSecurityProperties.Cookie(true, "Lax"),
                new ApplicationSecurityProperties.RateLimit(5, 300, 120),
                new ApplicationSecurityProperties.Credentials(
                        Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofMinutes(10), 12, 128),
                new ApplicationSecurityProperties.Secrets("01234567890123456789012345678901"));
        DeploymentSecurityProperties deployment = new DeploymentSecurityProperties(
                false, "X-Gbsw-Client-IP", List.of(),
                100, Duration.ofMinutes(15), 30, 2,
                memoryEntries, Duration.ofMinutes(15), Duration.ofHours(24),
                Duration.ofDays(30), 100, Duration.ofHours(1));
        return new AuthenticationThrottleService(repository, security, deployment);
    }
}
