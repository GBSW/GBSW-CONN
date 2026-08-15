package kr.hs.gbsw.communication.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.ThrottleState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ThrottleRepository {

    private final JdbcTemplate jdbcTemplate;

    public ThrottleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ThrottleState lockOrCreate(
            String scope,
            UUID accountUserId,
            Instant now,
            Instant expiresAt
    ) {
        jdbcTemplate.update("""
                        INSERT IGNORE INTO security_throttle_states (
                            throttle_scope, account_user_id, failure_count, expires_at
                        ) VALUES (?, UUID_TO_BIN(?), 0, ?)
                        """,
                scope, accountUserId.toString(), Timestamp.from(expiresAt));
        jdbcTemplate.update("""
                        UPDATE security_throttle_states
                        SET failure_count = 0, blocked_until = NULL, last_failure_at = NULL,
                            updated_at = ?, expires_at = ?
                        WHERE throttle_scope = ? AND account_user_id = UUID_TO_BIN(?)
                          AND expires_at <= ?
                        """,
                Timestamp.from(now),
                Timestamp.from(expiresAt),
                scope,
                accountUserId.toString(),
                Timestamp.from(now));
        List<ThrottleState> rows = jdbcTemplate.query("""
                        SELECT failure_count, blocked_until
                        FROM security_throttle_states
                        WHERE throttle_scope = ? AND account_user_id = UUID_TO_BIN(?)
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> {
                    Timestamp blockedUntil = resultSet.getTimestamp("blocked_until");
                    return new ThrottleState(
                            resultSet.getInt("failure_count"),
                            blockedUntil == null ? null : blockedUntil.toInstant());
                },
                scope, accountUserId.toString());
        if (rows.size() != 1) {
            throw new IllegalStateException("Throttle state could not be locked");
        }
        return rows.getFirst();
    }

    public void recordFailure(
            String scope,
            UUID accountUserId,
            int failureCount,
            Instant blockedUntil,
            Instant now,
            Instant expiresAt
    ) {
        jdbcTemplate.update("""
                        UPDATE security_throttle_states
                        SET failure_count = ?, blocked_until = ?, last_failure_at = ?,
                            updated_at = ?, expires_at = ?
                        WHERE throttle_scope = ? AND account_user_id = UUID_TO_BIN(?)
                        """,
                failureCount,
                blockedUntil == null ? null : Timestamp.from(blockedUntil),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(expiresAt),
                scope,
                accountUserId.toString());
    }

    public void clear(String scope, UUID accountUserId) {
        jdbcTemplate.update("""
                        DELETE FROM security_throttle_states
                        WHERE throttle_scope = ? AND account_user_id = UUID_TO_BIN(?)
                        """,
                scope, accountUserId.toString());
    }

    public int deleteExpired(Instant now, int limit) {
        return jdbcTemplate.update("""
                        DELETE FROM security_throttle_states
                        WHERE expires_at <= ?
                        ORDER BY expires_at, id
                        LIMIT ?
                        """,
                Timestamp.from(now), limit);
    }
}
