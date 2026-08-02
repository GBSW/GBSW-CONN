package kr.hs.gbsw.communication.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import kr.hs.gbsw.communication.auth.domain.ThrottleState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ThrottleRepository {

    private final JdbcTemplate jdbcTemplate;

    public ThrottleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ThrottleState lockOrCreate(String scope, byte[] fingerprint) {
        jdbcTemplate.update("""
                        INSERT IGNORE INTO security_throttle_states (
                            throttle_scope, subject_fingerprint, failure_count
                        ) VALUES (?, ?, 0)
                        """,
                scope, fingerprint);
        List<ThrottleState> rows = jdbcTemplate.query("""
                        SELECT failure_count, blocked_until
                        FROM security_throttle_states
                        WHERE throttle_scope = ? AND subject_fingerprint = ?
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> {
                    Timestamp blockedUntil = resultSet.getTimestamp("blocked_until");
                    return new ThrottleState(
                            resultSet.getInt("failure_count"),
                            blockedUntil == null ? null : blockedUntil.toInstant());
                },
                scope, fingerprint);
        if (rows.size() != 1) {
            throw new IllegalStateException("Throttle state could not be locked");
        }
        return rows.getFirst();
    }

    public void recordFailure(
            String scope,
            byte[] fingerprint,
            int failureCount,
            Instant blockedUntil,
            Instant now
    ) {
        jdbcTemplate.update("""
                        UPDATE security_throttle_states
                        SET failure_count = ?, blocked_until = ?, last_failure_at = ?, updated_at = ?
                        WHERE throttle_scope = ? AND subject_fingerprint = ?
                        """,
                failureCount,
                blockedUntil == null ? null : Timestamp.from(blockedUntil),
                Timestamp.from(now),
                Timestamp.from(now),
                scope,
                fingerprint);
    }

    public void clear(String scope, byte[] fingerprint) {
        jdbcTemplate.update("""
                        DELETE FROM security_throttle_states
                        WHERE throttle_scope = ? AND subject_fingerprint = ?
                        """,
                scope, fingerprint);
    }
}
