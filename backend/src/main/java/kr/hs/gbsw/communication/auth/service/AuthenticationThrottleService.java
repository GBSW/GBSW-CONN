package kr.hs.gbsw.communication.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.hs.gbsw.communication.auth.domain.ThrottleOperation;
import kr.hs.gbsw.communication.auth.domain.ThrottleState;
import kr.hs.gbsw.communication.auth.exception.AuthenticationThrottledException;
import kr.hs.gbsw.communication.auth.repository.ThrottleRepository;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import kr.hs.gbsw.communication.common.config.DeploymentSecurityProperties;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationThrottleService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ThrottleRepository repository;
    private final ApplicationSecurityProperties properties;
    private final DeploymentSecurityProperties deploymentProperties;
    private final SecretKeySpec fingerprintKey;
    private final Map<String, MemoryThrottleState> memoryStates =
            new LinkedHashMap<>(128, 0.75f, true);

    public AuthenticationThrottleService(
            ThrottleRepository repository,
            ApplicationSecurityProperties properties,
            DeploymentSecurityProperties deploymentProperties
    ) {
        this.repository = repository;
        this.properties = properties;
        this.deploymentProperties = deploymentProperties;
        this.fingerprintKey = new SecretKeySpec(
                properties.secrets().throttleFingerprint().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM);
    }

    public void assertAllowed(ThrottleOperation operation, String loginId, String remoteAddress, Instant now) {
        assertAllowed(operation, null, loginId, remoteAddress, now);
    }

    public void assertAllowed(
            ThrottleOperation operation,
            UUID accountUserId,
            String loginId,
            String remoteAddress,
            Instant now
    ) {
        if (isMemoryBlocked(memoryKey(operation.ipScope(), remoteAddress), now)) {
            throw new AuthenticationThrottledException();
        }
        if (accountUserId == null) {
            if (isMemoryBlocked(memoryKey(operation.accountScope(), loginId), now)) {
                throw new AuthenticationThrottledException();
            }
            return;
        }

        ThrottleState account = repository.lockOrCreate(
                operation.accountScope(),
                accountUserId,
                now,
                now.plus(deploymentProperties.persistentThrottleTtl()));
        if (account.isBlockedAt(now)) {
            throw new AuthenticationThrottledException();
        }
    }

    public void recordFailure(ThrottleOperation operation, String loginId, String remoteAddress, Instant now) {
        recordFailure(operation, null, loginId, remoteAddress, now);
    }

    public void recordFailure(
            ThrottleOperation operation,
            UUID accountUserId,
            String loginId,
            String remoteAddress,
            Instant now
    ) {
        recordMemoryFailure(memoryKey(operation.ipScope(), remoteAddress), now);
        if (accountUserId == null) {
            recordMemoryFailure(memoryKey(operation.accountScope(), loginId), now);
            return;
        }
        recordPersistentFailure(operation.accountScope(), accountUserId, now);
    }

    public void clear(ThrottleOperation operation, String loginId, String remoteAddress) {
        clear(operation, null, loginId, remoteAddress);
    }

    public void clear(
            ThrottleOperation operation,
            UUID accountUserId,
            String loginId,
            String remoteAddress
    ) {
        if (accountUserId == null) {
            clearMemory(memoryKey(operation.accountScope(), loginId));
        } else {
            repository.clear(operation.accountScope(), accountUserId);
        }
    }

    private void recordPersistentFailure(String scope, UUID accountUserId, Instant now) {
        Instant expiresAt = now.plus(deploymentProperties.persistentThrottleTtl());
        ThrottleState state = repository.lockOrCreate(scope, accountUserId, now, expiresAt);
        int failures = state.failureCount() + 1;
        repository.recordFailure(
                scope,
                accountUserId,
                failures,
                blockedUntil(failures, now),
                now,
                expiresAt);
    }

    private void recordMemoryFailure(String key, Instant now) {
        synchronized (memoryStates) {
            MemoryThrottleState current = memoryStates.get(key);
            int failures = current == null || !current.expiresAt().isAfter(now)
                    ? 1
                    : current.failureCount() + 1;
            if (current == null && memoryStates.size() >= deploymentProperties.unknownAccountMaxEntries()) {
                Iterator<String> eldest = memoryStates.keySet().iterator();
                if (eldest.hasNext()) {
                    eldest.next();
                    eldest.remove();
                }
            }
            memoryStates.put(key, new MemoryThrottleState(
                    failures,
                    blockedUntil(failures, now),
                    now.plus(deploymentProperties.unknownAccountTtl())));
        }
    }

    private boolean isMemoryBlocked(String key, Instant now) {
        synchronized (memoryStates) {
            MemoryThrottleState state = memoryStates.get(key);
            if (state == null) {
                return false;
            }
            if (!state.expiresAt().isAfter(now)) {
                memoryStates.remove(key);
                return false;
            }
            return state.blockedUntil() != null && state.blockedUntil().isAfter(now);
        }
    }

    private void clearMemory(String key) {
        synchronized (memoryStates) {
            memoryStates.remove(key);
        }
    }

    private Instant blockedUntil(int failures, Instant now) {
        int threshold = properties.rateLimit().loginFailuresBeforeDelay();
        if (failures < threshold) {
            return null;
        }
        int exponent = Math.min(30, failures - threshold);
        long delaySeconds = Math.min(
                properties.rateLimit().maximumDelaySeconds(),
                1L << exponent);
        return now.plusSeconds(delaySeconds);
    }

    private String memoryKey(String scope, String subject) {
        return scope + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(fingerprint(subject));
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

    int memoryStateCount() {
        synchronized (memoryStates) {
            return memoryStates.size();
        }
    }

    private record MemoryThrottleState(int failureCount, Instant blockedUntil, Instant expiresAt) {
    }
}
