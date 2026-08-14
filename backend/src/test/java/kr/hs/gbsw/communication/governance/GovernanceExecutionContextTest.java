package kr.hs.gbsw.communication.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import kr.hs.gbsw.communication.governance.service.GovernanceExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class GovernanceExecutionContextTest {

    @Test
    void rejectsDirectExecutionAndClearsContextAfterApprovedExecution() {
        assertThrows(AccessDeniedException.class, GovernanceExecutionContext::requireActive);
        assertEquals("executed", GovernanceExecutionContext.execute(() -> {
            GovernanceExecutionContext.requireActive();
            return "executed";
        }));
        assertThrows(AccessDeniedException.class, GovernanceExecutionContext::requireActive);
    }

    @Test
    void clearsContextWhenExecutionFails() {
        assertThrows(IllegalStateException.class, () -> GovernanceExecutionContext.execute(() -> {
            throw new IllegalStateException("failed");
        }));
        assertThrows(AccessDeniedException.class, GovernanceExecutionContext::requireActive);
    }
}
