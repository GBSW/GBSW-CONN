package kr.hs.gbsw.communication.proposal.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.domain.AuthorVisibility;
import kr.hs.gbsw.communication.proposal.domain.EncryptedProposalIdentity;
import kr.hs.gbsw.communication.proposal.domain.LockedProposal;
import kr.hs.gbsw.communication.proposal.domain.ProposalFeedScope;
import kr.hs.gbsw.communication.proposal.domain.ProposalCommentRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalSort;
import kr.hs.gbsw.communication.proposal.domain.ProposalStatusHistoryRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalViewRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalVisibilityStatus;
import kr.hs.gbsw.communication.proposal.domain.ProposalWorkflowStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProposalRepository {

    private static final String VALID_SUPPORT_COUNT = """
            (
                SELECT COUNT(*)
                FROM proposal_supports ps
                JOIN users supporter ON supporter.id = ps.voter_user_id
                WHERE ps.proposal_id = p.id
                  AND supporter.account_status = 'ACTIVE'
                  AND EXISTS (
                      SELECT 1
                      FROM role_assignments supporter_role
                      WHERE supporter_role.user_id = supporter.id
                        AND supporter_role.role_type = 'STUDENT'
                        AND supporter_role.starts_at <= ?
                        AND (supporter_role.ends_at IS NULL OR supporter_role.ends_at > ?)
                  )
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProposalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertProposal(
            UUID id,
            UUID publicId,
            String title,
            String content,
            AuthorVisibility authorVisibility,
            String authorDisplayName,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO proposals (
                            id, public_id, title, content, author_visibility, author_display_name,
                            workflow_status, visibility_status, created_at, updated_at
                        ) VALUES (
                            UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?,
                            'GATHERING_SUPPORT', 'VISIBLE', ?, ?
                        )
                        """,
                id.toString(), publicId.toString(), title, content,
                authorVisibility.name(), authorDisplayName,
                Timestamp.from(now), Timestamp.from(now));
    }

    public void insertIdentity(
            UUID proposalId,
            EncryptedProposalIdentity identity,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO proposal_identities (
                            proposal_id, encrypted_user_id, nonce, key_version, created_at
                        ) VALUES (UUID_TO_BIN(?), ?, ?, ?, ?)
                        """,
                proposalId.toString(), identity.ciphertext(), identity.nonce(),
                identity.keyVersion(), Timestamp.from(now));
    }

    public boolean insertSupport(UUID proposalId, UUID voterUserId, Instant now) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO proposal_supports (proposal_id, voter_user_id, created_at)
                            VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?)
                            """,
                    proposalId.toString(), voterUserId.toString(), Timestamp.from(now));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public boolean isActiveStudent(UUID userId, Instant now) {
        Integer active = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM users user_account
                            JOIN role_assignments student_role ON student_role.user_id = user_account.id
                            WHERE user_account.id = UUID_TO_BIN(?)
                              AND user_account.account_status = 'ACTIVE'
                              AND student_role.role_type = 'STUDENT'
                              AND student_role.starts_at <= ?
                              AND (student_role.ends_at IS NULL OR student_role.ends_at > ?)
                        )
                        """,
                Integer.class,
                userId.toString(), Timestamp.from(now), Timestamp.from(now));
        return active != null && active == 1;
    }

    public boolean deleteSupport(UUID proposalId, UUID voterUserId) {
        return jdbcTemplate.update("""
                        DELETE FROM proposal_supports
                        WHERE proposal_id = UUID_TO_BIN(?) AND voter_user_id = UUID_TO_BIN(?)
                        """,
                proposalId.toString(), voterUserId.toString()) == 1;
    }

    public int countValidSupports(UUID proposalId, Instant now) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM proposal_supports ps
                        JOIN users supporter ON supporter.id = ps.voter_user_id
                        WHERE ps.proposal_id = UUID_TO_BIN(?)
                          AND supporter.account_status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1
                              FROM role_assignments supporter_role
                              WHERE supporter_role.user_id = supporter.id
                                AND supporter_role.role_type = 'STUDENT'
                                AND supporter_role.starts_at <= ?
                                AND (supporter_role.ends_at IS NULL OR supporter_role.ends_at > ?)
                          )
                        """,
                Integer.class,
                proposalId.toString(), Timestamp.from(now), Timestamp.from(now));
        return count == null ? 0 : count;
    }

    public Optional<LockedProposal> lockVisibleByPublicId(UUID publicId) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(public_id) AS public_id,
                               workflow_status, visibility_status
                        FROM proposals
                        WHERE public_id = UUID_TO_BIN(?)
                          AND visibility_status = 'VISIBLE'
                          AND withdrawn_at IS NULL
                        FOR UPDATE
                        """,
                this::mapLockedProposal,
                publicId.toString()).stream().findFirst();
    }

    public Optional<EncryptedProposalIdentity> findEncryptedIdentity(UUID publicId) {
        return jdbcTemplate.query("""
                        SELECT identity.encrypted_user_id, identity.nonce, identity.key_version
                        FROM proposal_identities identity
                        JOIN proposals proposal ON proposal.id = identity.proposal_id
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                        """,
                (resultSet, rowNumber) -> new EncryptedProposalIdentity(
                        resultSet.getBytes("encrypted_user_id"),
                        resultSet.getBytes("nonce"),
                        resultSet.getInt("key_version")),
                publicId.toString()).stream().findFirst();
    }

    public boolean updateDraft(UUID proposalId, String title, String content, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE proposals
                        SET title = ?, content = ?, updated_at = ?
                        WHERE id = UUID_TO_BIN(?)
                          AND workflow_status = 'GATHERING_SUPPORT'
                          AND visibility_status = 'VISIBLE'
                          AND withdrawn_at IS NULL
                        """,
                title, content, Timestamp.from(now), proposalId.toString()) == 1;
    }

    public boolean withdrawDraft(UUID proposalId, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE proposals
                        SET withdrawn_at = ?, updated_at = ?
                        WHERE id = UUID_TO_BIN(?)
                          AND workflow_status = 'GATHERING_SUPPORT'
                          AND visibility_status = 'VISIBLE'
                          AND withdrawn_at IS NULL
                        """,
                Timestamp.from(now), Timestamp.from(now), proposalId.toString()) == 1;
    }

    public ProposalCommentRecord insertComment(
            UUID proposalId,
            UUID commentPublicId,
            UUID authorUserId,
            String authorDisplayName,
            String content,
            Instant now
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO proposal_comments (
                            id, public_id, proposal_id, author_user_id, content, created_at
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?)
                        """,
                id.toString(), commentPublicId.toString(), proposalId.toString(),
                authorUserId.toString(), content, Timestamp.from(now));
        return new ProposalCommentRecord(
                commentPublicId, authorDisplayName, content, true, now);
    }

    public List<ProposalCommentRecord> findComments(UUID proposalPublicId, UUID viewerUserId) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(comment.public_id) AS public_id,
                               author.display_name AS author_display_name,
                               comment.content,
                               comment.author_user_id = UUID_TO_BIN(?) AS viewer_can_delete,
                               comment.created_at
                        FROM proposal_comments comment
                        JOIN proposals proposal ON proposal.id = comment.proposal_id
                        JOIN users author ON author.id = comment.author_user_id
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                          AND comment.deleted_at IS NULL
                        ORDER BY comment.created_at, comment.id
                        """,
                (resultSet, rowNumber) -> new ProposalCommentRecord(
                        UUID.fromString(resultSet.getString("public_id")),
                        resultSet.getString("author_display_name"),
                        resultSet.getString("content"),
                        resultSet.getBoolean("viewer_can_delete"),
                        resultSet.getTimestamp("created_at").toInstant()),
                viewerUserId.toString(), proposalPublicId.toString());
    }

    public boolean deleteComment(
            UUID proposalPublicId,
            UUID commentPublicId,
            UUID authorUserId,
            Instant now
    ) {
        return jdbcTemplate.update("""
                        UPDATE proposal_comments comment
                        JOIN proposals proposal ON proposal.id = comment.proposal_id
                        SET comment.deleted_at = ?
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                          AND comment.public_id = UUID_TO_BIN(?)
                          AND comment.author_user_id = UUID_TO_BIN(?)
                          AND comment.deleted_at IS NULL
                          AND proposal.visibility_status = 'VISIBLE'
                          AND proposal.withdrawn_at IS NULL
                        """,
                Timestamp.from(now), proposalPublicId.toString(), commentPublicId.toString(),
                authorUserId.toString()) == 1;
    }

    public boolean formalize(UUID proposalId, int supportCount, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE proposals
                        SET workflow_status = 'FORMAL_AGENDA',
                            formalized_at = ?,
                            formalized_support_count = ?,
                            updated_at = ?
                        WHERE id = UUID_TO_BIN(?) AND workflow_status = 'GATHERING_SUPPORT'
                        """,
                Timestamp.from(now), supportCount, Timestamp.from(now), proposalId.toString()) == 1;
    }

    public void insertStatusHistory(
            UUID proposalId,
            ProposalWorkflowStatus fromStatus,
            ProposalWorkflowStatus toStatus,
            UUID actorUserId,
            Integer supportCountSnapshot,
            String reason,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO proposal_status_history (
                            proposal_id, from_status, to_status, changed_by_user_id,
                            support_count_snapshot, reason, created_at
                        ) VALUES (
                            UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), ?, ?, ?
                        )
                        """,
                proposalId.toString(), fromStatus == null ? null : fromStatus.name(), toStatus.name(),
                actorUserId == null ? null : actorUserId.toString(),
                supportCountSnapshot, reason, Timestamp.from(now));
    }

    public void insertFormalAgendaNotification(UUID proposalId, Instant now) {
        jdbcTemplate.update("""
                        INSERT INTO proposal_notifications (
                            proposal_id, notification_type, created_at
                        ) VALUES (UUID_TO_BIN(?), 'FORMAL_AGENDA_CREATED', ?)
                        """,
                proposalId.toString(), Timestamp.from(now));
    }

    public List<ProposalViewRecord> findFeed(
            UUID viewerUserId,
            boolean studentView,
            ProposalFeedScope scope,
            String query,
            ProposalSort sort,
            Instant now,
            int size,
            long offset
    ) {
        FeedFilter filter = feedFilter(studentView, scope, query);
        List<Object> parameters = new ArrayList<>();
        parameters.add(Timestamp.from(now));
        parameters.add(Timestamp.from(now));
        parameters.add(viewerUserId.toString());
        parameters.addAll(filter.parameters());
        parameters.add(size);
        parameters.add(offset);
        String orderBy = sort == ProposalSort.MOST_SUPPORTED
                ? "support_count DESC, p.created_at DESC, p.id"
                : "p.created_at DESC, p.id";

        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(p.public_id) AS public_id,
                               p.title,
                               LEFT(p.content, 240) AS content,
                               p.author_visibility,
                               p.author_display_name,
                               p.workflow_status,
                               p.visibility_status,
                               """ + VALID_SUPPORT_COUNT + """
                               AS support_count,
                               EXISTS (
                                   SELECT 1 FROM proposal_supports viewer_support
                                   WHERE viewer_support.proposal_id = p.id
                                     AND viewer_support.voter_user_id = UUID_TO_BIN(?)
                               ) AS viewer_supported,
                               p.formalized_at,
                               p.formalized_support_count,
                               p.created_at
                        FROM proposals p
                        """ + filter.sql() + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                this::mapProposalView,
                parameters.toArray());
    }

    public long countFeed(boolean studentView, ProposalFeedScope scope, String query) {
        FeedFilter filter = feedFilter(studentView, scope, query);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposals p " + filter.sql(),
                Long.class,
                filter.parameters().toArray());
        return count == null ? 0 : count;
    }

    public Optional<ProposalViewRecord> findDetail(
            UUID publicId,
            UUID viewerUserId,
            boolean studentView,
            Instant now
    ) {
        String accessPredicate = studentView ? "" : " AND p.workflow_status <> 'GATHERING_SUPPORT'";
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(p.public_id) AS public_id,
                               p.title,
                               p.content,
                               p.author_visibility,
                               p.author_display_name,
                               p.workflow_status,
                               p.visibility_status,
                               """ + VALID_SUPPORT_COUNT + """
                               AS support_count,
                               EXISTS (
                                   SELECT 1 FROM proposal_supports viewer_support
                                   WHERE viewer_support.proposal_id = p.id
                                     AND viewer_support.voter_user_id = UUID_TO_BIN(?)
                               ) AS viewer_supported,
                               p.formalized_at,
                               p.formalized_support_count,
                               p.created_at
                        FROM proposals p
                        WHERE p.public_id = UUID_TO_BIN(?)
                          AND p.visibility_status = 'VISIBLE'
                          AND p.withdrawn_at IS NULL
                        """ + accessPredicate,
                this::mapProposalView,
                Timestamp.from(now), Timestamp.from(now),
                viewerUserId.toString(), publicId.toString()).stream().findFirst();
    }

    public List<ProposalStatusHistoryRecord> findStatusHistory(UUID publicId) {
        return jdbcTemplate.query("""
                        SELECT history.from_status, history.to_status,
                               history.support_count_snapshot, history.reason, history.created_at
                        FROM proposal_status_history history
                        JOIN proposals proposal ON proposal.id = history.proposal_id
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                        ORDER BY history.created_at, history.id
                        """,
                (resultSet, rowNumber) -> new ProposalStatusHistoryRecord(
                        nullableWorkflowStatus(resultSet, "from_status"),
                        ProposalWorkflowStatus.valueOf(resultSet.getString("to_status")),
                        nullableInteger(resultSet, "support_count_snapshot"),
                        resultSet.getString("reason"),
                        resultSet.getTimestamp("created_at").toInstant()),
                publicId.toString());
    }

    private FeedFilter feedFilter(boolean studentView, ProposalFeedScope scope, String query) {
        List<String> predicates = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        predicates.add("p.visibility_status = 'VISIBLE'");
        predicates.add("p.withdrawn_at IS NULL");
        if (!studentView || scope == ProposalFeedScope.FORMAL_AGENDA) {
            predicates.add("p.workflow_status <> 'GATHERING_SUPPORT'");
        }
        if (query != null && !query.isBlank()) {
            String escaped = "%" + escapeLike(query.strip()) + "%";
            predicates.add("(p.title LIKE ? ESCAPE '!' OR p.content LIKE ? ESCAPE '!')");
            parameters.add(escaped);
            parameters.add(escaped);
        }
        return new FeedFilter("WHERE " + String.join(" AND ", predicates), parameters);
    }

    private LockedProposal mapLockedProposal(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LockedProposal(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("public_id")),
                ProposalWorkflowStatus.valueOf(resultSet.getString("workflow_status")),
                ProposalVisibilityStatus.valueOf(resultSet.getString("visibility_status")));
    }

    private ProposalViewRecord mapProposalView(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp formalizedAt = resultSet.getTimestamp("formalized_at");
        return new ProposalViewRecord(
                UUID.fromString(resultSet.getString("public_id")),
                resultSet.getString("title"),
                resultSet.getString("content"),
                AuthorVisibility.valueOf(resultSet.getString("author_visibility")),
                resultSet.getString("author_display_name"),
                ProposalWorkflowStatus.valueOf(resultSet.getString("workflow_status")),
                ProposalVisibilityStatus.valueOf(resultSet.getString("visibility_status")),
                resultSet.getInt("support_count"),
                resultSet.getBoolean("viewer_supported"),
                formalizedAt == null ? null : formalizedAt.toInstant(),
                nullableInteger(resultSet, "formalized_support_count"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private ProposalWorkflowStatus nullableWorkflowStatus(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : ProposalWorkflowStatus.valueOf(value);
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private record FeedFilter(String sql, List<Object> parameters) {
    }
}
