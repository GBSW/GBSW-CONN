package kr.hs.gbsw.communication.audit.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void append(
            UUID actorUserId,
            String eventType,
            UUID targetPublicId,
            String outcome,
            String traceId,
            Instant now
    ) {
        appendForTarget(actorUserId, eventType, "USER", targetPublicId, outcome, traceId, now);
    }

    public void appendForTarget(
            UUID actorUserId,
            String eventType,
            String targetType,
            UUID targetPublicId,
            String outcome,
            String traceId,
            Instant now
    ) {
        appendForTargetWithRetention(
                actorUserId, eventType, targetType, targetPublicId,
                outcome, traceId, "SECURITY", now);
    }

    public void appendAuthentication(
            UUID actorUserId,
            String eventType,
            UUID targetPublicId,
            String outcome,
            String traceId,
            Instant now
    ) {
        if (actorUserId == null) {
            appendUnknownAuthenticationAggregate(eventType, outcome, traceId, now);
            return;
        }
        appendForTargetWithRetention(
                actorUserId, eventType, "USER", targetPublicId,
                outcome, traceId, "AUTH_TRANSIENT", now);
    }

    private void appendForTargetWithRetention(
            UUID actorUserId,
            String eventType,
            String targetType,
            UUID targetPublicId,
            String outcome,
            String traceId,
            String retentionClass,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO audit_logs (
                            actor_user_id, event_type, target_type, target_public_id,
                            outcome, trace_id, details_json, created_at,
                            retention_class, occurrence_count, last_occurred_at
                        ) VALUES (
                            IF(? IS NULL, NULL, UUID_TO_BIN(?)), ?, ?,
                            IF(? IS NULL, NULL, UUID_TO_BIN(?)), ?, ?, NULL, ?, ?, 1, ?
                        )
                        """,
                uuidString(actorUserId),
                uuidString(actorUserId),
                eventType,
                targetType,
                uuidString(targetPublicId),
                uuidString(targetPublicId),
                outcome,
                traceId,
                Timestamp.from(now),
                retentionClass,
                Timestamp.from(now));
    }

    private void appendUnknownAuthenticationAggregate(
            String eventType,
            String outcome,
            String traceId,
            Instant now
    ) {
        Instant bucket = now.truncatedTo(ChronoUnit.HOURS);
        jdbcTemplate.update("""
                        INSERT INTO audit_logs (
                            actor_user_id, event_type, target_type, target_public_id,
                            outcome, trace_id, details_json, created_at,
                            retention_class, occurrence_count, aggregation_bucket, last_occurred_at
                        ) VALUES (
                            NULL, ?, 'USER', NULL, ?, ?, JSON_OBJECT('aggregated', TRUE), ?,
                            'AUTH_TRANSIENT', 1, ?, ?
                        )
                        ON DUPLICATE KEY UPDATE
                            occurrence_count = occurrence_count + 1,
                            trace_id = VALUES(trace_id),
                            last_occurred_at = VALUES(last_occurred_at)
                        """,
                eventType,
                outcome,
                traceId,
                Timestamp.from(now),
                Timestamp.from(bucket),
                Timestamp.from(now));
    }

    public int deleteExpiredAuthenticationEvents(Instant cutoff, int limit) {
        return jdbcTemplate.update("""
                        DELETE FROM audit_logs
                        WHERE retention_class = 'AUTH_TRANSIENT' AND last_occurred_at < ?
                        ORDER BY last_occurred_at, id
                        LIMIT ?
                        """,
                Timestamp.from(cutoff), limit);
    }

    public void appendWithReason(
            UUID actorUserId,
            String eventType,
            UUID targetPublicId,
            String outcome,
            String traceId,
            String reason,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO audit_logs (
                            actor_user_id, event_type, target_type, target_public_id,
                            outcome, trace_id, details_json, created_at
                        ) VALUES (
                            UUID_TO_BIN(?), ?, 'USER', UUID_TO_BIN(?), ?, ?,
                            JSON_OBJECT('reason', ?), ?
                        )
                        """,
                actorUserId.toString(),
                eventType,
                targetPublicId.toString(),
                outcome,
                traceId,
                reason,
                Timestamp.from(now));
    }

    private String uuidString(UUID value) {
        return value == null ? null : value.toString();
    }
}
