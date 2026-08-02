package kr.hs.gbsw.communication.auth.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AccountRecord;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.auth.domain.SessionAccountState;
import kr.hs.gbsw.communication.auth.domain.StoredOneTimeCode;
import kr.hs.gbsw.communication.user.domain.AccountStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {

    private static final String ACCOUNT_COLUMNS = """
            SELECT BIN_TO_UUID(u.id) AS id,
                   BIN_TO_UUID(u.public_id) AS public_id,
                   u.login_id,
                   u.display_name,
                   u.account_status,
                   c.password_hash,
                   c.credential_version
            FROM users u
            LEFT JOIN credentials c ON c.user_id = u.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AccountRecord> findByLoginId(String loginId) {
        List<AccountRecord> rows = jdbcTemplate.query(
                ACCOUNT_COLUMNS + " WHERE u.login_id = ?",
                this::mapAccount,
                loginId);
        return rows.stream().findFirst();
    }

    public Optional<AccountRecord> lockByLoginId(String loginId) {
        List<AccountRecord> rows = jdbcTemplate.query(
                ACCOUNT_COLUMNS + " WHERE u.login_id = ? FOR UPDATE",
                this::mapAccount,
                loginId);
        return rows.stream().findFirst();
    }

    public Optional<AccountRecord> lockByPublicId(UUID publicId) {
        List<AccountRecord> rows = jdbcTemplate.query(
                ACCOUNT_COLUMNS + " WHERE u.public_id = UUID_TO_BIN(?) FOR UPDATE",
                this::mapAccount,
                publicId.toString());
        return rows.stream().findFirst();
    }

    public Optional<AccountRecord> findByPublicId(UUID publicId) {
        List<AccountRecord> rows = jdbcTemplate.query(
                ACCOUNT_COLUMNS + " WHERE u.public_id = UUID_TO_BIN(?)",
                this::mapAccount,
                publicId.toString());
        return rows.stream().findFirst();
    }

    public List<StoredOneTimeCode> lockUsableActivationCodes(UUID userId, Instant now) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(id) AS id, code_hash, expires_at
                        FROM activation_codes
                        WHERE user_id = UUID_TO_BIN(?)
                          AND used_at IS NULL
                          AND revoked_at IS NULL
                          AND expires_at > ?
                        ORDER BY created_at DESC
                        LIMIT 1
                        FOR UPDATE
                        """,
                this::mapOneTimeCode,
                userId.toString(),
                Timestamp.from(now));
    }

    public List<StoredOneTimeCode> lockUsablePasswordResetCodes(UUID userId, Instant now) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(id) AS id, token_hash AS code_hash, expires_at
                        FROM password_reset_tokens
                        WHERE user_id = UUID_TO_BIN(?)
                          AND used_at IS NULL
                          AND revoked_at IS NULL
                          AND expires_at > ?
                        ORDER BY created_at DESC
                        LIMIT 1
                        FOR UPDATE
                        """,
                this::mapOneTimeCode,
                userId.toString(),
                Timestamp.from(now));
    }

    public void activateAccount(UUID userId, String passwordHash, Instant now) {
        jdbcTemplate.update("""
                        INSERT INTO credentials (
                            user_id, password_hash, credential_version, password_changed_at, created_at, updated_at
                        ) VALUES (UUID_TO_BIN(?), ?, 1, ?, ?, ?)
                        """,
                userId.toString(), passwordHash, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        int updated = jdbcTemplate.update("""
                        UPDATE users
                        SET account_status = 'ACTIVE', activated_at = ?, suspended_at = NULL, updated_at = ?
                        WHERE id = UUID_TO_BIN(?) AND account_status = 'PENDING_ACTIVATION'
                        """,
                Timestamp.from(now), Timestamp.from(now), userId.toString());
        if (updated != 1) {
            throw new IllegalStateException("Pending account activation update did not affect exactly one row");
        }
    }

    public void markActivationCodeUsed(UUID codeId, Instant now) {
        int updated = jdbcTemplate.update("""
                        UPDATE activation_codes
                        SET used_at = ?
                        WHERE id = UUID_TO_BIN(?) AND used_at IS NULL AND revoked_at IS NULL
                        """,
                Timestamp.from(now), codeId.toString());
        if (updated != 1) {
            throw new IllegalStateException("Activation code was not consumed exactly once");
        }
    }

    public void revokeOtherActivationCodes(UUID userId, UUID usedCodeId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE activation_codes
                        SET revoked_at = ?
                        WHERE user_id = UUID_TO_BIN(?)
                          AND id <> UUID_TO_BIN(?)
                          AND used_at IS NULL
                          AND revoked_at IS NULL
                        """,
                Timestamp.from(now), userId.toString(), usedCodeId.toString());
    }

    public void resetPassword(UUID userId, String passwordHash, Instant now) {
        int updated = jdbcTemplate.update("""
                        UPDATE credentials
                        SET password_hash = ?,
                            credential_version = credential_version + 1,
                            password_changed_at = ?,
                            updated_at = ?
                        WHERE user_id = UUID_TO_BIN(?)
                        """,
                passwordHash, Timestamp.from(now), Timestamp.from(now), userId.toString());
        if (updated != 1) {
            throw new IllegalStateException("Password reset did not affect exactly one credential");
        }
    }

    public void markPasswordResetCodeUsed(UUID codeId, Instant now) {
        int updated = jdbcTemplate.update("""
                        UPDATE password_reset_tokens
                        SET used_at = ?
                        WHERE id = UUID_TO_BIN(?) AND used_at IS NULL AND revoked_at IS NULL
                        """,
                Timestamp.from(now), codeId.toString());
        if (updated != 1) {
            throw new IllegalStateException("Password reset code was not consumed exactly once");
        }
    }

    public void revokeOtherPasswordResetCodes(UUID userId, UUID usedCodeId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE password_reset_tokens
                        SET revoked_at = ?
                        WHERE user_id = UUID_TO_BIN(?)
                          AND id <> UUID_TO_BIN(?)
                          AND used_at IS NULL
                          AND revoked_at IS NULL
                        """,
                Timestamp.from(now), userId.toString(), usedCodeId.toString());
    }

    public void deleteSessions(String principalName) {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", principalName);
    }

    public AuthPrincipal toPrincipal(AccountRecord account, Instant now, Instant reauthenticatedAt) {
        if (!account.hasCredentials()) {
            throw new IllegalArgumentException("An authenticated principal requires credentials");
        }
        return new AuthPrincipal(
                account.id(),
                account.publicId(),
                account.loginId(),
                account.displayName(),
                account.credentialVersion(),
                reauthenticatedAt,
                findAuthorities(account.id(), now));
    }

    public Optional<SessionAccountState> findSessionState(UUID userId, Instant now) {
        List<AccountRecord> rows = jdbcTemplate.query(
                ACCOUNT_COLUMNS + " WHERE u.id = UUID_TO_BIN(?)",
                this::mapAccount,
                userId.toString());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        AccountRecord account = rows.getFirst();
        if (!account.hasCredentials()) {
            return Optional.empty();
        }
        return Optional.of(new SessionAccountState(
                account.status() == AccountStatus.ACTIVE,
                account.credentialVersion(),
                findAuthorities(userId, now)));
    }

    private List<String> findAuthorities(UUID userId, Instant now) {
        List<String> authorities = new ArrayList<>();
        authorities.addAll(jdbcTemplate.queryForList("""
                        SELECT CONCAT('ROLE_', role_type)
                        FROM role_assignments
                        WHERE user_id = UUID_TO_BIN(?)
                          AND starts_at <= ?
                          AND (ends_at IS NULL OR ends_at > ?)
                        ORDER BY role_type
                        """,
                String.class,
                userId.toString(), Timestamp.from(now), Timestamp.from(now)));
        authorities.addAll(jdbcTemplate.queryForList("""
                        SELECT CONCAT('OFFICE_', office_type)
                        FROM office_assignments
                        WHERE user_id = UUID_TO_BIN(?)
                          AND starts_at <= ?
                          AND (ends_at IS NULL OR ends_at > ?)
                        ORDER BY office_type
                        """,
                String.class,
                userId.toString(), Timestamp.from(now), Timestamp.from(now)));
        return List.copyOf(authorities);
    }

    private AccountRecord mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        Object credentialVersion = resultSet.getObject("credential_version");
        return new AccountRecord(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("public_id")),
                resultSet.getString("login_id"),
                resultSet.getString("display_name"),
                AccountStatus.valueOf(resultSet.getString("account_status")),
                resultSet.getString("password_hash"),
                credentialVersion == null ? null : ((Number) credentialVersion).longValue());
    }

    private StoredOneTimeCode mapOneTimeCode(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StoredOneTimeCode(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getString("code_hash"),
                resultSet.getTimestamp("expires_at").toInstant());
    }
}
