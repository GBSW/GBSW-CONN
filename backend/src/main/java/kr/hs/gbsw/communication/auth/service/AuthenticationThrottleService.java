package kr.hs.gbsw.communication.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.hs.gbsw.communication.auth.domain.ThrottleOperation;
import kr.hs.gbsw.communication.auth.domain.ThrottleState;
import kr.hs.gbsw.communication.auth.exception.AuthenticationThrottledException;
import kr.hs.gbsw.communication.auth.repository.ThrottleRepository;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationThrottleService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ThrottleRepository repository;
    private final ApplicationSecurityProperties properties;
    private final SecretKeySpec fingerprintKey;

    public AuthenticationThrottleService(
            ThrottleRepository repository,
            ApplicationSecurityProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
        this.fingerprintKey = new SecretKeySpec(
                properties.secrets().throttleFingerprint().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM);
    }

    public void assertAllowed(ThrottleOperation operation, String loginId, String remoteAddress, Instant now) {
        ThrottleState account = repository.lockOrCreate(
                operation.accountScope(), fingerprint("account:" + loginId));
        ThrottleState ip = repository.lockOrCreate(
                operation.ipScope(), fingerprint("ip:" + remoteAddress));
        if (account.isBlockedAt(now) || ip.isBlockedAt(now)) {
            throw new AuthenticationThrottledException();
        }
    }

    public void recordFailure(ThrottleOperation operation, String loginId, String remoteAddress, Instant now) {
        recordFailure(operation.accountScope(), fingerprint("account:" + loginId), now);
        recordFailure(operation.ipScope(), fingerprint("ip:" + remoteAddress), now);
    }

    public void clear(ThrottleOperation operation, String loginId, String remoteAddress) {
        repository.clear(operation.accountScope(), fingerprint("account:" + loginId));
        repository.clear(operation.ipScope(), fingerprint("ip:" + remoteAddress));
    }

    private void recordFailure(String scope, byte[] fingerprint, Instant now) {
        ThrottleState state = repository.lockOrCreate(scope, fingerprint);
        int failures = state.failureCount() + 1;
        int threshold = properties.rateLimit().loginFailuresBeforeDelay();
        Instant blockedUntil = null;
        if (failures >= threshold) {
            int exponent = Math.min(30, failures - threshold);
            long delaySeconds = Math.min(
                    properties.rateLimit().maximumDelaySeconds(),
                    1L << exponent);
            blockedUntil = now.plusSeconds(delaySeconds);
        }
        repository.recordFailure(scope, fingerprint, failures, blockedUntil, now);
    }

    private byte[] fingerprint(String subject) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(fingerprintKey);
            return mac.doFinal(subject.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create security throttle fingerprint", exception);
        }
    }
}
