package kr.hs.gbsw.communication.auth.domain;

import java.time.Instant;

public record ThrottleState(int failureCount, Instant blockedUntil) {
    public boolean isBlockedAt(Instant now) {
        return blockedUntil != null && blockedUntil.isAfter(now);
    }
}
