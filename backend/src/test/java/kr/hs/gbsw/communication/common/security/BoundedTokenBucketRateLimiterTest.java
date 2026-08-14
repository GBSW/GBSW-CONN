package kr.hs.gbsw.communication.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BoundedTokenBucketRateLimiterTest {

    @Test
    void enforcesCapacityRefillsAndBoundsTrackedKeys() {
        BoundedTokenBucketRateLimiter limiter =
                new BoundedTokenBucketRateLimiter(2, 2, Duration.ofMinutes(10));
        Instant now = Instant.parse("2026-08-14T00:00:00Z");

        assertThat(limiter.tryAcquire("first", now)).isTrue();
        assertThat(limiter.tryAcquire("first", now)).isTrue();
        assertThat(limiter.tryAcquire("first", now)).isFalse();
        assertThat(limiter.tryAcquire("first", now.plusSeconds(30))).isTrue();

        assertThat(limiter.tryAcquire("second", now)).isTrue();
        assertThat(limiter.tryAcquire("third", now)).isTrue();
        assertThat(limiter.trackedKeyCount()).isEqualTo(2);
    }
}
