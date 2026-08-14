package kr.hs.gbsw.communication.governance.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.hs.gbsw.communication.governance.domain.PrivilegedChangeRequestRecord;
import kr.hs.gbsw.communication.governance.domain.PrivilegedChangeStatus;
import kr.hs.gbsw.communication.governance.domain.PrivilegedChangeType;
import kr.hs.gbsw.communication.governance.dto.request.PrivilegedChangeCreateRequest;
import kr.hs.gbsw.communication.office.domain.OfficeType;
import kr.hs.gbsw.communication.user.domain.AccountRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PrivilegedChangeRepository {

    private static final String SELECT_REQUEST = """
            SELECT BIN_TO_UUID(request.id) AS id,
                   BIN_TO_UUID(request.public_id) AS public_id,
                   request.change_type, request.change_status,
                   BIN_TO_UUID(request.target_user_id) AS target_user_id,
                   BIN_TO_UUID(target.public_id) AS target_user_public_id,
                   request.login_id, request.display_name, request.role_type, request.office_type,
                   request.starts_at, request.ends_at, request.replace_existing_at_start,
                   request.reason,
                   BIN_TO_UUID(request.requested_by_user_id) AS requested_by_user_id,
                   BIN_TO_UUID(requester.public_id) AS requested_by_public_id,
                   requester.display_name AS requested_by_display_name,
                   request.requested_at, request.expires_at,
                   BIN_TO_UUID(approver.public_id) AS approved_by_public_id,
                   approver.display_name AS approved_by_display_name,
                   request.executed_at,
                   delivery.delivery_status
            FROM privileged_change_requests request
            JOIN users requester ON requester.id = request.requested_by_user_id
            LEFT JOIN users target ON target.id = request.target_user_id
            LEFT JOIN users approver ON approver.id = request.approved_by_user_id
            LEFT JOIN credential_delivery_records delivery
              ON delivery.privileged_change_request_id = request.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public PrivilegedChangeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(
            UUID id,
            UUID publicId,
            UUID targetUserId,
            AuthenticatedRequester requester,
            PrivilegedChangeCreateRequest request,
            Instant requestedAt,
            Instant expiresAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO privileged_change_requests (
                            id, public_id, change_type, change_status, target_user_id,
                            login_id, display_name, role_type, office_type,
                            starts_at, ends_at, replace_existing_at_start, reason,
                            requested_by_user_id, requested_at, expires_at
                        ) VALUES (
                            UUID_TO_BIN(?), UUID_TO_BIN(?), ?, 'PENDING',
                            IF(? IS NULL, NULL, UUID_TO_BIN(?)),
                            ?, ?, ?, ?, ?, ?, ?, ?, UUID_TO_BIN(?), ?, ?
                        )
                        """,
                id.toString(), publicId.toString(), request.changeType().name(),
                uuidString(targetUserId), uuidString(targetUserId),
                nullableText(request.loginId()), nullableText(request.displayName()),
                enumName(request.role()), enumName(request.office()),
                timestamp(request.startsAt()), timestamp(request.endsAt()), request.replaceExisting(),
                request.reason().strip(), requester.userId().toString(),
                Timestamp.from(requestedAt), Timestamp.from(expiresAt));
    }

    public Optional<PrivilegedChangeRequestRecord> lockByPublicId(UUID publicId) {
        return jdbcTemplate.query(
                SELECT_REQUEST + " WHERE request.public_id = UUID_TO_BIN(?) FOR UPDATE",
                this::mapRequest,
                publicId.toString()).stream().findFirst();
    }

    public List<PrivilegedChangeRequestRecord> findRecent(int size) {
        return jdbcTemplate.query(
                SELECT_REQUEST + " ORDER BY request.requested_at DESC, request.id LIMIT ?",
                this::mapRequest,
                size);
    }

    public int lockProvisionedSuperAdministratorCount(Instant now) {
        return jdbcTemplate.queryForList("""
                        SELECT BIN_TO_UUID(user.id)
                        FROM users user
                        JOIN role_assignments role ON role.user_id = user.id
                        WHERE role.role_type = 'SUPER_ADMIN'
                          AND role.starts_at <= ?
                          AND (role.ends_at IS NULL OR role.ends_at > ?)
                          AND user.account_status <> 'WITHDRAWN'
                        FOR UPDATE
                        """,
                String.class, Timestamp.from(now), Timestamp.from(now)).size();
    }

    public boolean isPendingSuperAdministrator(UUID userId, Instant now) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM users user
                        JOIN role_assignments role ON role.user_id = user.id
                        WHERE user.id = UUID_TO_BIN(?)
                          AND user.account_status = 'PENDING_ACTIVATION'
                          AND role.role_type = 'SUPER_ADMIN'
                          AND role.starts_at <= ?
                          AND (role.ends_at IS NULL OR role.ends_at > ?)
                        """,
                Integer.class, userId.toString(), Timestamp.from(now), Timestamp.from(now));
        return count != null && count == 1;
    }

    public void attachTargetUser(UUID requestId, UUID targetUserId) {
        int updated = jdbcTemplate.update("""
                        UPDATE privileged_change_requests
                        SET target_user_id = UUID_TO_BIN(?)
                        WHERE id = UUID_TO_BIN(?) AND target_user_id IS NULL
                        """,
                targetUserId.toString(), requestId.toString());
        if (updated != 1) {
            throw new IllegalStateException("Privileged change target was not attached exactly once");
        }
    }

    public void markExecuted(
            UUID requestId,
            UUID requesterUserId,
            UUID approverUserId,
            boolean bootstrapQuorumException,
            String approvalReason,
            Instant now
    ) {
        int updated = jdbcTemplate.update("""
                        UPDATE privileged_change_requests
                        SET change_status = 'EXECUTED',
                            approved_by_user_id = UUID_TO_BIN(?),
                            bootstrap_quorum_exception = ?,
                            approval_reason = ?, approved_at = ?, executed_at = ?
                        WHERE id = UUID_TO_BIN(?)
                          AND change_status = 'PENDING'
                          AND requested_by_user_id = UUID_TO_BIN(?)
                          AND (requested_by_user_id <> UUID_TO_BIN(?) OR ? = TRUE)
                          AND expires_at > ?
                        """,
                approverUserId.toString(), bootstrapQuorumException,
                approvalReason.strip(), Timestamp.from(now), Timestamp.from(now),
                requestId.toString(), requesterUserId.toString(), approverUserId.toString(),
                bootstrapQuorumException, Timestamp.from(now));
        if (updated != 1) {
            throw new IllegalStateException("Privileged change was not executed exactly once");
        }
    }

    private PrivilegedChangeRequestRecord mapRequest(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PrivilegedChangeRequestRecord(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("public_id")),
                PrivilegedChangeType.valueOf(resultSet.getString("change_type")),
                PrivilegedChangeStatus.valueOf(resultSet.getString("change_status")),
                nullableUuid(resultSet, "target_user_id"),
                nullableUuid(resultSet, "target_user_public_id"),
                resultSet.getString("login_id"), resultSet.getString("display_name"),
                nullableEnum(resultSet.getString("role_type"), AccountRole.class),
                nullableEnum(resultSet.getString("office_type"), OfficeType.class),
                nullableInstant(resultSet, "starts_at"), nullableInstant(resultSet, "ends_at"),
                resultSet.getBoolean("replace_existing_at_start"), resultSet.getString("reason"),
                UUID.fromString(resultSet.getString("requested_by_user_id")),
                UUID.fromString(resultSet.getString("requested_by_public_id")),
                resultSet.getString("requested_by_display_name"),
                resultSet.getTimestamp("requested_at").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant(),
                nullableUuid(resultSet, "approved_by_public_id"),
                resultSet.getString("approved_by_display_name"),
                nullableInstant(resultSet, "executed_at"), resultSet.getString("delivery_status"));
    }

    private UUID nullableUuid(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private <T extends Enum<T>> T nullableEnum(String value, Class<T> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private String uuidString(UUID value) {
        return value == null ? null : value.toString();
    }

    private String nullableText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    public record AuthenticatedRequester(UUID userId) {
    }
}
