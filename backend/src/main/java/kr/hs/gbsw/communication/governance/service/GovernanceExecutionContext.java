package kr.hs.gbsw.communication.governance.service;

import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;

public final class GovernanceExecutionContext {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private GovernanceExecutionContext() {
    }

    public static <T> T execute(Supplier<T> operation) {
        if (Boolean.TRUE.equals(ACTIVE.get())) {
            throw new IllegalStateException("Nested governance execution is not allowed");
        }
        ACTIVE.set(true);
        try {
            return operation.get();
        } finally {
            ACTIVE.remove();
        }
    }

    public static void requireActive() {
        if (!Boolean.TRUE.equals(ACTIVE.get())) {
            throw new AccessDeniedException("This change requires an approved governance request");
        }
    }
}
