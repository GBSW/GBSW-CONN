package kr.hs.gbsw.communication.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class BoundedTokenBucketRateLimiter {

    private static final double NANOS_PER_MINUTE = Duration.ofMinutes(1).toNanos();

    private final int capacity;
    private final int maxEntries;
    private final Duration idleTtl;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>(128, 0.75f, true);

    public BoundedTokenBucketRateLimiter(int capacity, int maxEntries, Duration idleTtl) {
        if (capacity < 1 || maxEntries < 1 || idleTtl.isZero() || idleTtl.isNegative()) {
            throw new IllegalArgumentException("Rate limiter values must be positive");
        }
        this.capacity = capacity;
        this.maxEntries = maxEntries;
        this.idleTtl = idleTtl;
    }

    public synchronized boolean tryAcquire(String key, Instant now) {
        Bucket bucket = buckets.get(key);
        if (bucket != null && !bucket.lastSeen().plus(idleTtl).isAfter(now)) {
            buckets.remove(key);
            bucket = null;
        }
        if (bucket == null) {
            evictIfFull();
            buckets.put(key, new Bucket(capacity - 1.0, now, now));
            return true;
        }

        long elapsedNanos = Math.max(0, Duration.between(bucket.lastRefill(), now).toNanos());
        double tokens = Math.min(capacity, bucket.tokens() + elapsedNanos * capacity / NANOS_PER_MINUTE);
        if (tokens < 1.0) {
            buckets.put(key, new Bucket(tokens, now, now));
            return false;
        }
        buckets.put(key, new Bucket(tokens - 1.0, now, now));
        return true;
    }

    private void evictIfFull() {
        if (buckets.size() < maxEntries) {
            return;
        }
        Iterator<String> eldest = buckets.keySet().iterator();
        if (eldest.hasNext()) {
            eldest.next();
            eldest.remove();
        }
    }

    int trackedKeyCount() {
        return buckets.size();
    }

    private record Bucket(double tokens, Instant lastRefill, Instant lastSeen) {
    }
}
