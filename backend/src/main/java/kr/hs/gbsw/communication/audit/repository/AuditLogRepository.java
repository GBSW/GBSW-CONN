package kr.hs.gbsw.communication.audit.repository;

import java.sql.Timestamp;
import java.time.Instant;
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
        jdbcTemplate.update("""
                        INSERT INTO audit_logs (
                            actor_user_id, event_type, target_type, target_public_id,
                            outcome, trace_id, details_json, created_at
                        ) VALUES (
                            IF(? IS NULL, NULL, UUID_TO_BIN(?)), ?, ?,
                            IF(? IS NULL, NULL, UUID_TO_BIN(?)), ?, ?, NULL, ?
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
                Timestamp.from(now));
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
