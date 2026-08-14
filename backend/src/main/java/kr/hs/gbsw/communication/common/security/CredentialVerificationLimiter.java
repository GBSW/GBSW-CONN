package kr.hs.gbsw.communication.common.security;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import kr.hs.gbsw.communication.auth.exception.AuthenticationThrottledException;
import kr.hs.gbsw.communication.common.config.DeploymentSecurityProperties;
import org.springframework.stereotype.Component;

@Component
public class CredentialVerificationLimiter {

    private final Semaphore permits;

    public CredentialVerificationLimiter(DeploymentSecurityProperties properties) {
        this.permits = new Semaphore(properties.credentialVerificationConcurrency(), true);
    }

    public <T> T execute(Supplier<T> operation) {
        if (!permits.tryAcquire()) {
            throw new AuthenticationThrottledException();
        }
        try {
            return operation.get();
        } finally {
            permits.release();
        }
    }
}
