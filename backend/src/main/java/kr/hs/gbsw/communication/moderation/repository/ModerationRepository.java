package kr.hs.gbsw.communication.moderation.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.hs.gbsw.communication.moderation.domain.ContentReportRecord;
import kr.hs.gbsw.communication.moderation.domain.LockedContentReport;
import kr.hs.gbsw.communication.moderation.domain.LockedModerationCase;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseStatus;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseType;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseViewRecord;
import kr.hs.gbsw.communication.moderation.domain.ModerationVoteDecision;
import kr.hs.gbsw.communication.moderation.domain.ModerationVoteRecord;
import kr.hs.gbsw.communication.moderation.domain.ProtectedProposalIdentity;
import kr.hs.gbsw.communication.moderation.domain.ReportReceipt;
import kr.hs.gbsw.communication.moderation.domain.RevealedIdentity;
import kr.hs.gbsw.communication.moderation.domain.ReviewerSnapshotCandidate;
import kr.hs.gbsw.communication.office.domain.OfficeType;
import kr.hs.gbsw.communication.proposal.domain.AuthorVisibility;
import kr.hs.gbsw.communication.proposal.domain.EncryptedProposalIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ModerationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModerationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertReport(
            UUID id,
            UUID publicId,
            UUID proposalId,
            UUID reporterUserId,
            String reason,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO content_reports (
                            id, public_id, proposal_id, reporter_user_id, reason, created_at
                        ) VALUES (
                            UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?
                        )
                        """,
                id.toString(), publicId.toString(), proposalId.toString(), reporterUserId.toString(),
                reason, Timestamp.from(now));
    }

    public Optional<ReportReceipt> findReportReceipt(UUID proposalId, UUID reporterUserId) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(public_id) AS public_id, created_at
                        FROM content_reports
                        WHERE proposal_id = UUID_TO_BIN(?) AND reporter_user_id = UUID_TO_BIN(?)
                        """,
                (resultSet, rowNumber) -> new ReportReceipt(
                        UUID.fromString(resultSet.getString("public_id")),
                        resultSet.getTimestamp("created_at").toInstant()),
                proposalId.toString(), reporterUserId.toString()).stream().findFirst();
    }

    public boolean isCurrentOffice(UUID userId, OfficeType office, Instant now) {
        Integer active = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM users user_account
                            JOIN office_assignments office_assignment
                              ON office_assignment.user_id = user_account.id
                            WHERE user_account.id = UUID_TO_BIN(?)
                              AND user_account.account_status = 'ACTIVE'
                              AND office_assignment.office_type = ?
                              AND office_assignment.starts_at <= ?
                              AND (office_assignment.ends_at IS NULL OR office_assignment.ends_at > ?)
                        )
                        """,
                Integer.class, userId.toString(), office.name(), Timestamp.from(now), Timestamp.from(now));
        return active != null && active == 1;
    }

    public List<ContentReportRecord> findInboxReports(int size) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(report.public_id) AS public_id,
                               BIN_TO_UUID(proposal.public_id) AS proposal_public_id,
                               proposal.title, proposal.content,
                               report.reason, report.created_at,
                               GROUP_CONCAT(moderation_case.case_type ORDER BY moderation_case.case_type SEPARATOR ',')
                                   AS case_types
                        FROM content_reports report
                        JOIN proposals proposal ON proposal.id = report.proposal_id
                        LEFT JOIN moderation_cases moderation_case
                          ON moderation_case.source_report_id = report.id
                        GROUP BY report.id, report.public_id, proposal.public_id,
                                 proposal.title, proposal.content, report.reason, report.created_at
                        ORDER BY report.created_at DESC, report.id
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new ContentReportRecord(
                        UUID.fromString(resultSet.getString("public_id")),
                        UUID.fromString(resultSet.getString("proposal_public_id")),
                        resultSet.getString("title"),
                        resultSet.getString("content"),
                        resultSet.getString("reason"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        parseCaseTypes(resultSet.getString("case_types"))),
                size);
    }

    public Optional<LockedContentReport> lockReport(UUID reportPublicId) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(report.id) AS id,
                               BIN_TO_UUID(report.proposal_id) AS proposal_id,
                               BIN_TO_UUID(proposal.public_id) AS proposal_public_id,
                               report.reason
                        FROM content_reports report
                        JOIN proposals proposal ON proposal.id = report.proposal_id
                        WHERE report.public_id = UUID_TO_BIN(?)
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> new LockedContentReport(
                        UUID.fromString(resultSet.getString("id")),
                        UUID.fromString(resultSet.getString("proposal_id")),
                        UUID.fromString(resultSet.getString("proposal_public_id")),
                        resultSet.getString("reason")),
                reportPublicId.toString()).stream().findFirst();
    }

    public List<ReviewerSnapshotCandidate> lockCurrentReviewerCandidates(Instant now) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(office_assignment.user_id) AS user_id,
                               office_assignment.office_type
                        FROM office_assignments office_assignment
                        JOIN users reviewer ON reviewer.id = office_assignment.user_id
                        WHERE office_assignment.office_type IN (
                            'STUDENT_AFFAIRS_TEACHER',
                            'STUDENT_COUNCIL_PRESIDENT',
                            'STUDENT_COUNCIL_VICE_PRESIDENT'
                        )
                          AND office_assignment.starts_at <= ?
                          AND (office_assignment.ends_at IS NULL OR office_assignment.ends_at > ?)
                          AND reviewer.account_status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1 FROM role_assignments reviewer_role
                              WHERE reviewer_role.user_id = reviewer.id
                                AND reviewer_role.role_type = CASE
                                    WHEN office_assignment.office_type = 'STUDENT_AFFAIRS_TEACHER'
                                        THEN 'TEACHER'
                                    ELSE 'STUDENT'
                                END
                                AND reviewer_role.starts_at <= ?
                                AND (reviewer_role.ends_at IS NULL OR reviewer_role.ends_at > ?)
                          )
                        ORDER BY office_assignment.office_type, office_assignment.user_id
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> new ReviewerSnapshotCandidate(
                        UUID.fromString(resultSet.getString("user_id")),
                        OfficeType.valueOf(resultSet.getString("office_type"))),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    public void insertCase(
            UUID id,
            UUID publicId,
            LockedContentReport report,
            ModerationCaseType type,
            UUID actorUserId,
            String reason,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO moderation_cases (
                            id, public_id, proposal_id, source_report_id, case_type,
                            case_status, created_by_user_id, reason, created_at
                        ) VALUES (
                            UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?,
                            'PENDING', UUID_TO_BIN(?), ?, ?
                        )
                        """,
                id.toString(), publicId.toString(), report.proposalId().toString(), report.id().toString(),
                type.name(), actorUserId.toString(), reason, Timestamp.from(now));
    }

    public boolean existsCase(UUID proposalId, ModerationCaseType type) {
        Integer exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM moderation_cases
                            WHERE proposal_id = UUID_TO_BIN(?) AND case_type = ?
                        )
                        """, Integer.class, proposalId.toString(), type.name());
        return exists != null && exists == 1;
    }

    public void insertReviewerSnapshot(
            UUID id,
            UUID caseId,
            ReviewerSnapshotCandidate reviewer,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO moderation_reviewer_snapshots (
                            id, case_id, reviewer_user_id, office_type, created_at
                        ) VALUES (
                            UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?
                        )
                        """,
                id.toString(), caseId.toString(), reviewer.userId().toString(),
                reviewer.office().name(), Timestamp.from(now));
    }

    public List<ModerationCaseViewRecord> findCasesForReviewer(UUID reviewerUserId, int size) {
        List<CaseBase> cases = jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(moderation_case.id) AS id,
                               BIN_TO_UUID(moderation_case.public_id) AS public_id,
                               moderation_case.case_type, moderation_case.case_status,
                               BIN_TO_UUID(proposal.public_id) AS proposal_public_id,
                               proposal.title, proposal.content,
                               proposal.author_visibility, proposal.author_display_name,
                               report.reason AS report_reason,
                               moderation_case.reason AS case_reason,
                               moderation_case.created_at, moderation_case.decided_at,
                               viewer_snapshot.office_type AS viewer_office,
                               EXISTS (
                                   SELECT 1 FROM moderation_votes viewer_vote
                                   WHERE viewer_vote.reviewer_snapshot_id = viewer_snapshot.id
                               ) AS viewer_voted,
                               EXISTS (
                                   SELECT 1 FROM identity_reveal_records reveal_record
                                   WHERE reveal_record.case_id = moderation_case.id
                               ) AS identity_revealed
                        FROM moderation_cases moderation_case
                        JOIN moderation_reviewer_snapshots viewer_snapshot
                          ON viewer_snapshot.case_id = moderation_case.id
                         AND viewer_snapshot.reviewer_user_id = UUID_TO_BIN(?)
                        JOIN proposals proposal ON proposal.id = moderation_case.proposal_id
                        JOIN content_reports report ON report.id = moderation_case.source_report_id
                        ORDER BY moderation_case.created_at DESC, moderation_case.id
                        LIMIT ?
                        """,
                this::mapCaseBase,
                reviewerUserId.toString(), size);
        return cases.stream().map(this::withVotes).toList();
    }

    public Optional<ModerationCaseViewRecord> findCaseForReviewer(UUID publicId, UUID reviewerUserId) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(moderation_case.id) AS id,
                               BIN_TO_UUID(moderation_case.public_id) AS public_id,
                               moderation_case.case_type, moderation_case.case_status,
                               BIN_TO_UUID(proposal.public_id) AS proposal_public_id,
                               proposal.title, proposal.content,
                               proposal.author_visibility, proposal.author_display_name,
                               report.reason AS report_reason,
                               moderation_case.reason AS case_reason,
                               moderation_case.created_at, moderation_case.decided_at,
                               viewer_snapshot.office_type AS viewer_office,
                               EXISTS (
                                   SELECT 1 FROM moderation_votes viewer_vote
                                   WHERE viewer_vote.reviewer_snapshot_id = viewer_snapshot.id
                               ) AS viewer_voted,
                               EXISTS (
                                   SELECT 1 FROM identity_reveal_records reveal_record
                                   WHERE reveal_record.case_id = moderation_case.id
                               ) AS identity_revealed
                        FROM moderation_cases moderation_case
                        JOIN moderation_reviewer_snapshots viewer_snapshot
                          ON viewer_snapshot.case_id = moderation_case.id
                         AND viewer_snapshot.reviewer_user_id = UUID_TO_BIN(?)
                        JOIN proposals proposal ON proposal.id = moderation_case.proposal_id
                        JOIN content_reports report ON report.id = moderation_case.source_report_id
                        WHERE moderation_case.public_id = UUID_TO_BIN(?)
                        """,
                this::mapCaseBase,
                reviewerUserId.toString(), publicId.toString()).stream()
                .findFirst().map(this::withVotes);
    }

    public Optional<LockedModerationCase> lockCaseForReviewer(UUID publicId, UUID reviewerUserId) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(moderation_case.id) AS id,
                               BIN_TO_UUID(moderation_case.public_id) AS public_id,
                               BIN_TO_UUID(moderation_case.proposal_id) AS proposal_id,
                               BIN_TO_UUID(proposal.public_id) AS proposal_public_id,
                               moderation_case.case_type, moderation_case.case_status,
                               moderation_case.decided_at,
                               BIN_TO_UUID(viewer_snapshot.id) AS reviewer_snapshot_id,
                               viewer_snapshot.office_type
                        FROM moderation_cases moderation_case
                        JOIN proposals proposal ON proposal.id = moderation_case.proposal_id
                        JOIN moderation_reviewer_snapshots viewer_snapshot
                          ON viewer_snapshot.case_id = moderation_case.id
                         AND viewer_snapshot.reviewer_user_id = UUID_TO_BIN(?)
                        WHERE moderation_case.public_id = UUID_TO_BIN(?)
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> new LockedModerationCase(
                        UUID.fromString(resultSet.getString("id")),
                        UUID.fromString(resultSet.getString("public_id")),
                        UUID.fromString(resultSet.getString("proposal_id")),
                        UUID.fromString(resultSet.getString("proposal_public_id")),
                        ModerationCaseType.valueOf(resultSet.getString("case_type")),
                        ModerationCaseStatus.valueOf(resultSet.getString("case_status")),
                        UUID.fromString(resultSet.getString("reviewer_snapshot_id")),
                        OfficeType.valueOf(resultSet.getString("office_type")),
                        toInstant(resultSet.getTimestamp("decided_at"))),
                reviewerUserId.toString(), publicId.toString()).stream().findFirst();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public boolean hasVote(UUID reviewerSnapshotId) {
        Integer voted = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM moderation_votes
                            WHERE reviewer_snapshot_id = UUID_TO_BIN(?)
                        )
                        """, Integer.class, reviewerSnapshotId.toString());
        return voted != null && voted == 1;
    }

    public void insertVote(
            UUID id,
            UUID reviewerSnapshotId,
            ModerationVoteDecision decision,
            String reason,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO moderation_votes (
                            id, reviewer_snapshot_id, decision, reason, created_at
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?)
                        """,
                id.toString(), reviewerSnapshotId.toString(), decision.name(), reason, Timestamp.from(now));
    }

    public VoteCounts countVotes(UUID caseId) {
        return jdbcTemplate.queryForObject("""
                        SELECT COUNT(vote.id) AS total_votes,
                               COALESCE(SUM(vote.decision = 'APPROVE'), 0) AS approvals,
                               COALESCE(SUM(vote.decision = 'REJECT'), 0) AS rejections
                        FROM moderation_reviewer_snapshots reviewer
                        LEFT JOIN moderation_votes vote ON vote.reviewer_snapshot_id = reviewer.id
                        WHERE reviewer.case_id = UUID_TO_BIN(?)
                        """,
                (resultSet, rowNumber) -> new VoteCounts(
                        resultSet.getInt("total_votes"),
                        resultSet.getInt("approvals"),
                        resultSet.getInt("rejections")),
                caseId.toString());
    }

    public void decideCase(UUID caseId, ModerationCaseStatus status, Instant now) {
        int updated = jdbcTemplate.update("""
                        UPDATE moderation_cases
                        SET case_status = ?, decided_at = ?
                        WHERE id = UUID_TO_BIN(?) AND case_status = 'PENDING'
                        """,
                status.name(), Timestamp.from(now), caseId.toString());
        if (updated != 1) {
            throw new IllegalStateException("Moderation case was not decided exactly once");
        }
    }

    public void hideProposalByDecision(UUID proposalId, UUID caseId, Instant now) {
        String fromStatus = jdbcTemplate.queryForObject("""
                        SELECT visibility_status FROM proposals
                        WHERE id = UUID_TO_BIN(?)
                        FOR UPDATE
                        """, String.class, proposalId.toString());
        if ("HIDDEN_BY_DECISION".equals(fromStatus)) {
            return;
        }
        int updated = jdbcTemplate.update("""
                        UPDATE proposals
                        SET visibility_status = 'HIDDEN_BY_DECISION', updated_at = ?
                        WHERE id = UUID_TO_BIN(?) AND visibility_status = ?
                        """,
                Timestamp.from(now), proposalId.toString(), fromStatus);
        if (updated != 1) {
            throw new IllegalStateException("Proposal visibility was not changed exactly once");
        }
        jdbcTemplate.update("""
                        INSERT INTO proposal_visibility_history (
                            proposal_id, from_status, to_status, moderation_case_id, created_at
                        ) VALUES (UUID_TO_BIN(?), ?, 'HIDDEN_BY_DECISION', UUID_TO_BIN(?), ?)
                        """,
                proposalId.toString(), fromStatus, caseId.toString(), Timestamp.from(now));
    }

    public boolean hasIdentityReveal(UUID caseId) {
        Integer revealed = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM identity_reveal_records WHERE case_id = UUID_TO_BIN(?)
                        )
                        """, Integer.class, caseId.toString());
        return revealed != null && revealed == 1;
    }

    public void insertIdentityReveal(
            UUID id,
            UUID caseId,
            UUID actorUserId,
            String reason,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO identity_reveal_records (
                            id, case_id, revealed_by_user_id, reason, revealed_at
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?)
                        """,
                id.toString(), caseId.toString(), actorUserId.toString(), reason, Timestamp.from(now));
    }

    public Optional<ProtectedProposalIdentity> findProtectedIdentity(UUID caseId) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(proposal.public_id) AS proposal_public_id,
                               identity.encrypted_user_id, identity.nonce, identity.key_version
                        FROM moderation_cases moderation_case
                        JOIN proposals proposal ON proposal.id = moderation_case.proposal_id
                        JOIN proposal_identities identity ON identity.proposal_id = proposal.id
                        WHERE moderation_case.id = UUID_TO_BIN(?)
                        """,
                (resultSet, rowNumber) -> new ProtectedProposalIdentity(
                        UUID.fromString(resultSet.getString("proposal_public_id")),
                        new EncryptedProposalIdentity(
                                resultSet.getBytes("encrypted_user_id"),
                                resultSet.getBytes("nonce"),
                                resultSet.getInt("key_version"))),
                caseId.toString()).stream().findFirst();
    }

    public Optional<RevealedIdentity> findIdentity(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT login_id, display_name FROM users WHERE id = UUID_TO_BIN(?)
                        """,
                (resultSet, rowNumber) -> new RevealedIdentity(
                        resultSet.getString("login_id"), resultSet.getString("display_name")),
                userId.toString()).stream().findFirst();
    }

    private CaseBase mapCaseBase(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        Timestamp decidedAt = resultSet.getTimestamp("decided_at");
        return new CaseBase(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("public_id")),
                ModerationCaseType.valueOf(resultSet.getString("case_type")),
                ModerationCaseStatus.valueOf(resultSet.getString("case_status")),
                UUID.fromString(resultSet.getString("proposal_public_id")),
                resultSet.getString("title"), resultSet.getString("content"),
                AuthorVisibility.valueOf(resultSet.getString("author_visibility")),
                resultSet.getString("author_display_name"),
                resultSet.getString("report_reason"), resultSet.getString("case_reason"),
                resultSet.getTimestamp("created_at").toInstant(),
                decidedAt == null ? null : decidedAt.toInstant(),
                OfficeType.valueOf(resultSet.getString("viewer_office")),
                resultSet.getBoolean("viewer_voted"), resultSet.getBoolean("identity_revealed"));
    }

    private ModerationCaseViewRecord withVotes(CaseBase caseBase) {
        return new ModerationCaseViewRecord(
                caseBase.publicId(), caseBase.type(), caseBase.status(), caseBase.proposalPublicId(),
                caseBase.title(), caseBase.content(), caseBase.authorVisibility(), caseBase.authorDisplayName(),
                caseBase.reportReason(), caseBase.caseReason(), caseBase.createdAt(), caseBase.decidedAt(),
                caseBase.viewerOffice(), caseBase.viewerVoted(), caseBase.identityRevealed(),
                findVotes(caseBase.id()));
    }

    private List<ModerationVoteRecord> findVotes(UUID caseId) {
        return jdbcTemplate.query("""
                        SELECT reviewer.office_type, vote.decision, vote.reason, vote.created_at
                        FROM moderation_votes vote
                        JOIN moderation_reviewer_snapshots reviewer
                          ON reviewer.id = vote.reviewer_snapshot_id
                        WHERE reviewer.case_id = UUID_TO_BIN(?)
                        ORDER BY vote.created_at, vote.id
                        """,
                (resultSet, rowNumber) -> new ModerationVoteRecord(
                        OfficeType.valueOf(resultSet.getString("office_type")),
                        ModerationVoteDecision.valueOf(resultSet.getString("decision")),
                        resultSet.getString("reason"),
                        resultSet.getTimestamp("created_at").toInstant()),
                caseId.toString());
    }

    private List<ModerationCaseType> parseCaseTypes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(ModerationCaseType::valueOf)
                .toList();
    }

    public record VoteCounts(int total, int approvals, int rejections) {
    }

    private record CaseBase(
            UUID id,
            UUID publicId,
            ModerationCaseType type,
            ModerationCaseStatus status,
            UUID proposalPublicId,
            String title,
            String content,
            AuthorVisibility authorVisibility,
            String authorDisplayName,
            String reportReason,
            String caseReason,
            Instant createdAt,
            Instant decidedAt,
            OfficeType viewerOffice,
            boolean viewerVoted,
            boolean identityRevealed
    ) {
    }
}
