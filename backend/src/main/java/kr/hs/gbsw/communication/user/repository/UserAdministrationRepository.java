package kr.hs.gbsw.communication.user.repository;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.office.domain.OfficeAssignmentRecord;
import kr.hs.gbsw.communication.office.domain.OfficeAssignmentHistory;
import kr.hs.gbsw.communication.office.domain.OfficeType;
import kr.hs.gbsw.communication.user.domain.AccountRole;
import kr.hs.gbsw.communication.user.domain.AccountStatus;
import kr.hs.gbsw.communication.user.domain.AccountSummaryRecord;
import kr.hs.gbsw.communication.user.domain.RoleAssignmentRecord;
import kr.hs.gbsw.communication.user.domain.RoleAssignmentHistory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class UserAdministrationRepository {

    private static final String SUPER_ADMIN_BOOTSTRAP_MARKER = "SUPER_ADMIN_BOOTSTRAP";

    private final JdbcTemplate jdbcTemplate;

    public UserAdministrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean acquireSuperAdminBootstrapMarker(Instant now) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO bootstrap_markers (marker_name, created_at)
                            VALUES (?, ?)
                            """,
                    SUPER_ADMIN_BOOTSTRAP_MARKER,
                    Timestamp.from(now));
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    public void completeSuperAdminBootstrapMarker(UUID userId, Instant now) {
        int updated = jdbcTemplate.update("""
                        UPDATE bootstrap_markers
                        SET completed_by_user_id = UUID_TO_BIN(?), completed_at = ?
                        WHERE marker_name = ? AND completed_by_user_id IS NULL
                        """,
                userId.toString(), Timestamp.from(now), SUPER_ADMIN_BOOTSTRAP_MARKER);
        if (updated != 1) {
            throw new IllegalStateException("Super admin bootstrap marker was not completed exactly once");
        }
    }

    public void insertPendingAccount(
            UUID userId,
            UUID publicId,
            String loginId,
            String displayName,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO users (
                            id, public_id, login_id, display_name, account_status, created_at, updated_at
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, 'PENDING_ACTIVATION', ?, ?)
                        """,
                userId.toString(),
                publicId.toString(),
                loginId,
                displayName,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public void insertRoleAssignment(
            UUID userId,
            AccountRole role,
            UUID assignedByUserId,
            Instant now,
            String reason
    ) {
        insertRoleAssignment(userId, role, assignedByUserId, now, null, now, reason);
    }

    public void insertRoleAssignment(
            UUID userId,
            AccountRole role,
            UUID assignedByUserId,
            Instant startsAt,
            Instant endsAt,
            Instant assignedAt,
            String reason
    ) {
        jdbcTemplate.update("""
                        INSERT INTO role_assignments (
                            id, user_id, role_type, starts_at, ends_at,
                            assigned_by_user_id, assigned_at, reason
                        ) VALUES (
                            UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?,
                            UUID_TO_BIN(?), ?, ?
                        )
                        """,
                UUID.randomUUID().toString(),
                userId.toString(),
                role.name(),
                Timestamp.from(startsAt),
                endsAt == null ? null : Timestamp.from(endsAt),
                assignedByUserId.toString(),
                Timestamp.from(assignedAt),
                reason);
    }

    public List<RoleAssignmentRecord> lockOverlappingRoleAssignments(
            UUID userId,
            AccountRole role,
            Instant startsAt,
            Instant endsAt
    ) {
        String endPredicate = endsAt == null ? "" : " AND starts_at < ?";
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(userId.toString());
        parameters.add(role.name());
        if (endsAt != null) {
            parameters.add(Timestamp.from(endsAt));
        }
        parameters.add(Timestamp.from(startsAt));
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(id) AS id, role_type, starts_at, ends_at
                        FROM role_assignments
                        WHERE user_id = UUID_TO_BIN(?) AND role_type = ?
                        """ + endPredicate + """
                          AND (ends_at IS NULL OR ends_at > ?)
                        ORDER BY starts_at
                        FOR UPDATE
                        """,
                this::mapRoleAssignment,
                parameters.toArray());
    }

    public List<RoleAssignmentRecord> lockRoleAssignmentAt(
            UUID userId,
            AccountRole role,
            Instant effectiveEnd
    ) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(id) AS id, role_type, starts_at, ends_at
                        FROM role_assignments
                        WHERE user_id = UUID_TO_BIN(?)
                          AND role_type = ?
                          AND starts_at < ?
                          AND (ends_at IS NULL OR ends_at > ?)
                        ORDER BY starts_at DESC
                        FOR UPDATE
                        """,
                this::mapRoleAssignment,
                userId.toString(), role.name(), Timestamp.from(effectiveEnd), Timestamp.from(effectiveEnd));
    }

    public boolean roleCoversPeriod(
            UUID userId,
            AccountRole role,
            Instant startsAt,
            Instant endsAt
    ) {
        String periodPredicate = endsAt == null
                ? " AND ends_at IS NULL"
                : " AND (ends_at IS NULL OR ends_at >= ?)";
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(userId.toString());
        parameters.add(role.name());
        parameters.add(Timestamp.from(startsAt));
        if (endsAt != null) {
            parameters.add(Timestamp.from(endsAt));
        }
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM role_assignments
                        WHERE user_id = UUID_TO_BIN(?)
                          AND role_type = ?
                          AND starts_at <= ?
                        """ + periodPredicate,
                Integer.class,
                parameters.toArray());
        return count != null && count == 1;
    }

    public boolean hasOverlappingRoleAssignment(
            UUID userId,
            AccountRole role,
            Instant startsAt,
            Instant endsAt
    ) {
        String endPredicate = endsAt == null ? "" : " AND starts_at < ?";
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(userId.toString());
        parameters.add(role.name());
        if (endsAt != null) {
            parameters.add(Timestamp.from(endsAt));
        }
        parameters.add(Timestamp.from(startsAt));
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM role_assignments
                        WHERE user_id = UUID_TO_BIN(?) AND role_type = ?
                        """ + endPredicate + """
                          AND (ends_at IS NULL OR ends_at > ?)
                        """,
                Integer.class, parameters.toArray());
        return count != null && count > 0;
    }

    public boolean hasOverlappingModerationOfficeAssignment(
            UUID userId,
            Instant startsAt,
            Instant endsAt
    ) {
        return hasOverlappingOfficeAssignment(userId, null, startsAt, endsAt);
    }

    public boolean hasOverlappingOtherModerationOfficeAssignment(
            UUID userId,
            OfficeType office,
            Instant startsAt,
            Instant endsAt
    ) {
        return hasOverlappingOfficeAssignment(userId, office, startsAt, endsAt);
    }

    public void lockModerationOfficeSeat(OfficeType office) {
        List<String> seats = jdbcTemplate.queryForList("""
                        SELECT office_type FROM moderation_office_seats
                        WHERE office_type = ?
                        FOR UPDATE
                        """,
                String.class, office.name());
        if (seats.size() != 1) {
            throw new IllegalStateException("Moderation office seat is not configured");
        }
    }

    private boolean hasOverlappingOfficeAssignment(
            UUID userId,
            OfficeType excludedOffice,
            Instant startsAt,
            Instant endsAt
    ) {
        String excludedPredicate = excludedOffice == null ? "" : " AND office_type <> ?";
        String endPredicate = endsAt == null ? "" : " AND starts_at < ?";
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(userId.toString());
        if (excludedOffice != null) {
            parameters.add(excludedOffice.name());
        }
        if (endsAt != null) {
            parameters.add(Timestamp.from(endsAt));
        }
        parameters.add(Timestamp.from(startsAt));
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM office_assignments
                        WHERE user_id = UUID_TO_BIN(?)
                        """ + excludedPredicate + endPredicate + """
                          AND (ends_at IS NULL OR ends_at > ?)
                        """,
                Integer.class, parameters.toArray());
        return count != null && count > 0;
    }

    public boolean hasOfficeAfterRoleEnd(UUID userId, AccountRole role, Instant effectiveEnd) {
        List<String> compatibleOffices = switch (role) {
            case STUDENT -> List.of(
                    OfficeType.STUDENT_COUNCIL_PRESIDENT.name(),
                    OfficeType.STUDENT_COUNCIL_VICE_PRESIDENT.name());
            case TEACHER -> List.of(OfficeType.STUDENT_AFFAIRS_TEACHER.name());
            case SUPER_ADMIN -> List.of();
        };
        if (compatibleOffices.isEmpty()) {
            return false;
        }
        String placeholders = String.join(",", compatibleOffices.stream().map(ignored -> "?").toList());
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(userId.toString());
        parameters.addAll(compatibleOffices);
        parameters.add(Timestamp.from(effectiveEnd));
        List<Integer> rows = jdbcTemplate.query("""
                        SELECT 1
                        FROM office_assignments
                        WHERE user_id = UUID_TO_BIN(?)
                          AND office_type IN (""" + placeholders + ")" + """
                          AND (ends_at IS NULL OR ends_at > ?)
                        LIMIT 1
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> 1,
                parameters.toArray());
        return !rows.isEmpty();
    }

    public void endRoleAssignment(
            UUID assignmentId,
            Instant effectiveEnd,
            UUID actorUserId,
            Instant now,
            String reason
    ) {
        int updated = jdbcTemplate.update("""
                        UPDATE role_assignments
                        SET ends_at = ?, ended_by_user_id = UUID_TO_BIN(?), ended_at = ?, end_reason = ?
                        WHERE id = UUID_TO_BIN(?)
                          AND starts_at < ?
                          AND (ends_at IS NULL OR ends_at > ?)
                        """,
                Timestamp.from(effectiveEnd), actorUserId.toString(), Timestamp.from(now), reason,
                assignmentId.toString(), Timestamp.from(effectiveEnd), Timestamp.from(effectiveEnd));
        if (updated != 1) {
            throw new IllegalStateException("Role assignment was not ended exactly once");
        }
    }

    public void insertOfficeAssignment(
            UUID userId,
            OfficeType office,
            Instant startsAt,
            Instant endsAt,
            UUID actorUserId,
            Instant now,
            String reason
    ) {
        jdbcTemplate.update("""
                        INSERT INTO office_assignments (
                            id, user_id, office_type, starts_at, ends_at,
                            assigned_by_user_id, assigned_at, reason
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, UUID_TO_BIN(?), ?, ?)
                        """,
                UUID.randomUUID().toString(), userId.toString(), office.name(),
                Timestamp.from(startsAt), endsAt == null ? null : Timestamp.from(endsAt),
                actorUserId.toString(), Timestamp.from(now), reason);
    }

    public List<OfficeAssignmentRecord> lockOverlappingOfficeAssignments(
            OfficeType office,
            Instant startsAt,
            Instant endsAt
    ) {
        String endPredicate = endsAt == null ? "" : " AND starts_at < ?";
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(office.name());
        if (endsAt != null) {
            parameters.add(Timestamp.from(endsAt));
        }
        parameters.add(Timestamp.from(startsAt));
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(user_id) AS user_id,
                               office_type, starts_at, ends_at
                        FROM office_assignments
                        WHERE office_type = ?
                        """ + endPredicate + """
                          AND (ends_at IS NULL OR ends_at > ?)
                        ORDER BY starts_at
                        FOR UPDATE
                        """,
                this::mapOfficeAssignment,
                parameters.toArray());
    }

    public List<OfficeAssignmentRecord> lockOfficeAssignmentAt(
            UUID userId,
            OfficeType office,
            Instant effectiveEnd
    ) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(user_id) AS user_id,
                               office_type, starts_at, ends_at
                        FROM office_assignments
                        WHERE user_id = UUID_TO_BIN(?)
                          AND office_type = ?
                          AND starts_at < ?
                          AND (ends_at IS NULL OR ends_at > ?)
                        ORDER BY starts_at DESC
                        FOR UPDATE
                        """,
                this::mapOfficeAssignment,
                userId.toString(), office.name(), Timestamp.from(effectiveEnd), Timestamp.from(effectiveEnd));
    }

    public void endOfficeAssignment(
            UUID assignmentId,
            Instant effectiveEnd,
            UUID actorUserId,
            Instant now,
            String reason
    ) {
        int updated = jdbcTemplate.update("""
                        UPDATE office_assignments
                        SET ends_at = ?, ended_by_user_id = UUID_TO_BIN(?), ended_at = ?, end_reason = ?
                        WHERE id = UUID_TO_BIN(?)
                          AND starts_at < ?
                          AND (ends_at IS NULL OR ends_at > ?)
                        """,
                Timestamp.from(effectiveEnd), actorUserId.toString(), Timestamp.from(now), reason,
                assignmentId.toString(), Timestamp.from(effectiveEnd), Timestamp.from(effectiveEnd));
        if (updated != 1) {
            throw new IllegalStateException("Office assignment was not ended exactly once");
        }
    }

    public void updateAccountStatus(UUID userId, String expectedStatus, String nextStatus, Instant now) {
        int updated = jdbcTemplate.update("""
                        UPDATE users
                        SET account_status = ?,
                            suspended_at = CASE WHEN ? = 'SUSPENDED' THEN ? ELSE NULL END,
                            updated_at = ?
                        WHERE id = UUID_TO_BIN(?) AND account_status = ?
                        """,
                nextStatus, nextStatus, Timestamp.from(now), Timestamp.from(now), userId.toString(), expectedStatus);
        if (updated != 1) {
            throw new IllegalStateException("Account status was not changed exactly once");
        }
    }

    public List<RoleAssignmentHistory> findRoleHistory(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT role_type, starts_at, ends_at, assigned_at, reason, ended_at, end_reason
                        FROM role_assignments
                        WHERE user_id = UUID_TO_BIN(?)
                        ORDER BY starts_at DESC, assigned_at DESC
                        """,
                (resultSet, rowNumber) -> new RoleAssignmentHistory(
                        AccountRole.valueOf(resultSet.getString("role_type")),
                        resultSet.getTimestamp("starts_at").toInstant(),
                        nullableInstant(resultSet, "ends_at"),
                        resultSet.getTimestamp("assigned_at").toInstant(),
                        resultSet.getString("reason"),
                        nullableInstant(resultSet, "ended_at"),
                        resultSet.getString("end_reason")),
                userId.toString());
    }

    public List<OfficeAssignmentHistory> findOfficeHistory(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT office_type, starts_at, ends_at, assigned_at, reason, ended_at, end_reason
                        FROM office_assignments
                        WHERE user_id = UUID_TO_BIN(?)
                        ORDER BY starts_at DESC, assigned_at DESC
                        """,
                (resultSet, rowNumber) -> new OfficeAssignmentHistory(
                        OfficeType.valueOf(resultSet.getString("office_type")),
                        resultSet.getTimestamp("starts_at").toInstant(),
                        nullableInstant(resultSet, "ends_at"),
                        resultSet.getTimestamp("assigned_at").toInstant(),
                        resultSet.getString("reason"),
                        nullableInstant(resultSet, "ended_at"),
                        resultSet.getString("end_reason")),
                userId.toString());
    }

    public List<AccountSummaryRecord> findAccounts(
            String query,
            AccountStatus status,
            Instant now,
            int size,
            long offset
    ) {
        QueryFilter filter = accountFilter(query, status);
        List<Object> parameters = new java.util.ArrayList<>();
        Timestamp effectiveAt = Timestamp.from(now);
        parameters.add(effectiveAt);
        parameters.add(effectiveAt);
        parameters.add(effectiveAt);
        parameters.add(effectiveAt);
        parameters.addAll(filter.parameters());
        parameters.add(size);
        parameters.add(offset);

        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(u.public_id) AS public_id,
                               u.login_id,
                               u.display_name,
                               u.account_status,
                               u.created_at,
                               (
                                   SELECT GROUP_CONCAT(ra.role_type ORDER BY ra.role_type SEPARATOR ',')
                                   FROM role_assignments ra
                                   WHERE ra.user_id = u.id
                                     AND ra.starts_at <= ?
                                     AND (ra.ends_at IS NULL OR ra.ends_at > ?)
                               ) AS current_roles,
                               (
                                   SELECT GROUP_CONCAT(oa.office_type ORDER BY oa.office_type SEPARATOR ',')
                                   FROM office_assignments oa
                                   WHERE oa.user_id = u.id
                                     AND oa.starts_at <= ?
                                     AND (oa.ends_at IS NULL OR oa.ends_at > ?)
                               ) AS current_offices
                        FROM users u
                        """ + filter.sql() + """
                        ORDER BY u.created_at DESC, u.login_id ASC
                        LIMIT ? OFFSET ?
                        """,
                this::mapAccountSummary,
                parameters.toArray());
    }

    public long countAccounts(String query, AccountStatus status) {
        QueryFilter filter = accountFilter(query, status);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users u " + filter.sql(),
                Long.class,
                filter.parameters().toArray());
        return count == null ? 0 : count;
    }

    public void revokeActivationCodes(UUID userId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE activation_codes
                        SET revoked_at = ?
                        WHERE user_id = UUID_TO_BIN(?) AND used_at IS NULL AND revoked_at IS NULL
                        """,
                Timestamp.from(now), userId.toString());
    }

    public void insertActivationCode(
            UUID userId,
            String codeHash,
            Instant expiresAt,
            UUID createdByUserId,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO activation_codes (
                            id, user_id, code_hash, expires_at, created_by_user_id, created_at
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), ?)
                        """,
                UUID.randomUUID().toString(),
                userId.toString(),
                codeHash,
                Timestamp.from(expiresAt),
                createdByUserId.toString(),
                Timestamp.from(now));
    }

    public void revokePasswordResetCodes(UUID userId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE password_reset_tokens
                        SET revoked_at = ?
                        WHERE user_id = UUID_TO_BIN(?) AND used_at IS NULL AND revoked_at IS NULL
                        """,
                Timestamp.from(now), userId.toString());
    }

    public void insertPasswordResetCode(
            UUID userId,
            String codeHash,
            Instant expiresAt,
            UUID createdByUserId,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO password_reset_tokens (
                            id, user_id, token_hash, expires_at, created_by_user_id, created_at
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), ?)
                        """,
                UUID.randomUUID().toString(),
                userId.toString(),
                codeHash,
                Timestamp.from(expiresAt),
                createdByUserId.toString(),
                Timestamp.from(now));
    }

    private RoleAssignmentRecord mapRoleAssignment(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp endsAt = resultSet.getTimestamp("ends_at");
        return new RoleAssignmentRecord(
                UUID.fromString(resultSet.getString("id")),
                AccountRole.valueOf(resultSet.getString("role_type")),
                resultSet.getTimestamp("starts_at").toInstant(),
                endsAt == null ? null : endsAt.toInstant());
    }

    private OfficeAssignmentRecord mapOfficeAssignment(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp endsAt = resultSet.getTimestamp("ends_at");
        return new OfficeAssignmentRecord(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("user_id")),
                OfficeType.valueOf(resultSet.getString("office_type")),
                resultSet.getTimestamp("starts_at").toInstant(),
                endsAt == null ? null : endsAt.toInstant());
    }

    private AccountSummaryRecord mapAccountSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AccountSummaryRecord(
                UUID.fromString(resultSet.getString("public_id")),
                resultSet.getString("login_id"),
                resultSet.getString("display_name"),
                AccountStatus.valueOf(resultSet.getString("account_status")),
                enumList(resultSet.getString("current_roles"), AccountRole.class),
                enumList(resultSet.getString("current_offices"), OfficeType.class),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private QueryFilter accountFilter(String query, AccountStatus status) {
        List<String> predicates = new java.util.ArrayList<>();
        List<Object> parameters = new java.util.ArrayList<>();
        if (query != null && !query.isBlank()) {
            String escapedQuery = "%" + escapeLike(query.strip()) + "%";
            predicates.add("(u.login_id LIKE ? ESCAPE '!' OR u.display_name LIKE ? ESCAPE '!')");
            parameters.add(escapedQuery);
            parameters.add(escapedQuery);
        }
        if (status != null) {
            predicates.add("u.account_status = ?");
            parameters.add(status.name());
        }
        String sql = predicates.isEmpty() ? "" : "WHERE " + String.join(" AND ", predicates);
        return new QueryFilter(sql, parameters);
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private <T extends Enum<T>> List<T> enumList(String value, Class<T> type) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(item -> Enum.valueOf(type, item))
                .toList();
    }

    private Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record QueryFilter(String sql, List<Object> parameters) {
    }
}
