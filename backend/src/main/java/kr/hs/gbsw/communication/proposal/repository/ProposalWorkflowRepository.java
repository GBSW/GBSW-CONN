package kr.hs.gbsw.communication.proposal.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.domain.AdminProposalRecord;
import kr.hs.gbsw.communication.proposal.domain.CurrentProposalTeacherAssignment;
import kr.hs.gbsw.communication.proposal.domain.EligibleProposalTeacher;
import kr.hs.gbsw.communication.proposal.domain.LockedProposal;
import kr.hs.gbsw.communication.proposal.domain.ProposalOfficialResponseRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalTeacherAssignmentRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalVisibilityStatus;
import kr.hs.gbsw.communication.proposal.domain.ProposalWorkflowStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProposalWorkflowRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProposalWorkflowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<LockedProposal> lockByPublicId(UUID publicId) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(public_id) AS public_id,
                               workflow_status, visibility_status
                        FROM proposals
                        WHERE public_id = UUID_TO_BIN(?)
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> new LockedProposal(
                        UUID.fromString(resultSet.getString("id")),
                        UUID.fromString(resultSet.getString("public_id")),
                        ProposalWorkflowStatus.valueOf(resultSet.getString("workflow_status")),
                        ProposalVisibilityStatus.valueOf(resultSet.getString("visibility_status"))),
                publicId.toString()).stream().findFirst();
    }

    public boolean isActiveRole(UUID userId, String role, Instant now) {
        Integer active = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM users user_account
                            JOIN role_assignments role_assignment ON role_assignment.user_id = user_account.id
                            WHERE user_account.id = UUID_TO_BIN(?)
                              AND user_account.account_status = 'ACTIVE'
                              AND role_assignment.role_type = ?
                              AND role_assignment.starts_at <= ?
                              AND (role_assignment.ends_at IS NULL OR role_assignment.ends_at > ?)
                        )
                        """,
                Integer.class, userId.toString(), role, Timestamp.from(now), Timestamp.from(now));
        return active != null && active == 1;
    }

    public Optional<EligibleProposalTeacher> findEligibleTeacher(UUID teacherPublicId, Instant now) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(teacher.id) AS id,
                               BIN_TO_UUID(teacher.public_id) AS public_id,
                               teacher.display_name
                        FROM users teacher
                        WHERE teacher.public_id = UUID_TO_BIN(?)
                          AND teacher.account_status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1 FROM role_assignments teacher_role
                              WHERE teacher_role.user_id = teacher.id
                                AND teacher_role.role_type = 'TEACHER'
                                AND teacher_role.starts_at <= ?
                                AND (teacher_role.ends_at IS NULL OR teacher_role.ends_at > ?)
                          )
                        """,
                (resultSet, rowNumber) -> new EligibleProposalTeacher(
                        UUID.fromString(resultSet.getString("id")),
                        UUID.fromString(resultSet.getString("public_id")),
                        resultSet.getString("display_name")),
                teacherPublicId.toString(), Timestamp.from(now), Timestamp.from(now))
                .stream().findFirst();
    }

    public List<EligibleProposalTeacher> findEligibleTeachers(String query, Instant now, int size) {
        String queryFilter = query == null ? "" : " AND teacher.display_name LIKE ? ESCAPE '!'";
        Object[] parameters = query == null
                ? new Object[]{Timestamp.from(now), Timestamp.from(now), size}
                : new Object[]{Timestamp.from(now), Timestamp.from(now), "%" + escapeLike(query) + "%", size};
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(teacher.id) AS id,
                               BIN_TO_UUID(teacher.public_id) AS public_id,
                               teacher.display_name
                        FROM users teacher
                        WHERE teacher.account_status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1 FROM role_assignments teacher_role
                              WHERE teacher_role.user_id = teacher.id
                                AND teacher_role.role_type = 'TEACHER'
                                AND teacher_role.starts_at <= ?
                                AND (teacher_role.ends_at IS NULL OR teacher_role.ends_at > ?)
                          )
                        """ + queryFilter + " ORDER BY teacher.display_name, teacher.id LIMIT ?",
                (resultSet, rowNumber) -> new EligibleProposalTeacher(
                        UUID.fromString(resultSet.getString("id")),
                        UUID.fromString(resultSet.getString("public_id")),
                        resultSet.getString("display_name")),
                parameters);
    }

    public Optional<CurrentProposalTeacherAssignment> findCurrentAssignment(UUID proposalId) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(assignment.id) AS id,
                               BIN_TO_UUID(assignment.teacher_user_id) AS teacher_user_id,
                               BIN_TO_UUID(teacher.public_id) AS teacher_public_id,
                               teacher.display_name AS teacher_display_name,
                               assignment.assigned_at
                        FROM proposal_teacher_assignments assignment
                        JOIN users teacher ON teacher.id = assignment.teacher_user_id
                        WHERE assignment.proposal_id = UUID_TO_BIN(?)
                          AND assignment.unassigned_at IS NULL
                        """,
                (resultSet, rowNumber) -> new CurrentProposalTeacherAssignment(
                        UUID.fromString(resultSet.getString("id")),
                        UUID.fromString(resultSet.getString("teacher_user_id")),
                        UUID.fromString(resultSet.getString("teacher_public_id")),
                        resultSet.getString("teacher_display_name"),
                        resultSet.getTimestamp("assigned_at").toInstant()),
                proposalId.toString()).stream().findFirst();
    }

    public boolean isCurrentAssignedTeacher(UUID proposalId, UUID teacherUserId, Instant now) {
        Integer assigned = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM proposal_teacher_assignments assignment
                            JOIN users teacher ON teacher.id = assignment.teacher_user_id
                            WHERE assignment.proposal_id = UUID_TO_BIN(?)
                              AND assignment.unassigned_at IS NULL
                              AND assignment.teacher_user_id = UUID_TO_BIN(?)
                              AND teacher.account_status = 'ACTIVE'
                              AND EXISTS (
                                  SELECT 1 FROM role_assignments teacher_role
                                  WHERE teacher_role.user_id = teacher.id
                                    AND teacher_role.role_type = 'TEACHER'
                                    AND teacher_role.starts_at <= ?
                                    AND (teacher_role.ends_at IS NULL OR teacher_role.ends_at > ?)
                              )
                        )
                        """,
                Integer.class, proposalId.toString(), teacherUserId.toString(),
                Timestamp.from(now), Timestamp.from(now));
        return assigned != null && assigned == 1;
    }

    public boolean isCurrentAssignedTeacherByPublicId(UUID publicId, UUID teacherUserId, Instant now) {
        Integer assigned = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM proposal_teacher_assignments assignment
                            JOIN proposals proposal ON proposal.id = assignment.proposal_id
                            JOIN users teacher ON teacher.id = assignment.teacher_user_id
                            WHERE proposal.public_id = UUID_TO_BIN(?)
                              AND assignment.unassigned_at IS NULL
                              AND assignment.teacher_user_id = UUID_TO_BIN(?)
                              AND teacher.account_status = 'ACTIVE'
                              AND EXISTS (
                                  SELECT 1 FROM role_assignments teacher_role
                                  WHERE teacher_role.user_id = teacher.id
                                    AND teacher_role.role_type = 'TEACHER'
                                    AND teacher_role.starts_at <= ?
                                    AND (teacher_role.ends_at IS NULL OR teacher_role.ends_at > ?)
                              )
                        )
                        """,
                Integer.class, publicId.toString(), teacherUserId.toString(),
                Timestamp.from(now), Timestamp.from(now));
        return assigned != null && assigned == 1;
    }

    public void endCurrentAssignment(UUID proposalId, UUID actorUserId, String reason, Instant now) {
        jdbcTemplate.update("""
                        UPDATE proposal_teacher_assignments
                        SET unassigned_by_user_id = UUID_TO_BIN(?),
                            unassignment_reason = ?,
                            unassigned_at = ?
                        WHERE proposal_id = UUID_TO_BIN(?) AND unassigned_at IS NULL
                        """,
                actorUserId.toString(), reason, Timestamp.from(now), proposalId.toString());
    }

    public void insertAssignment(
            UUID id,
            UUID proposalId,
            UUID teacherUserId,
            UUID actorUserId,
            String reason,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO proposal_teacher_assignments (
                            id, proposal_id, teacher_user_id, assigned_by_user_id,
                            assignment_reason, assigned_at
                        ) VALUES (
                            UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?
                        )
                        """,
                id.toString(), proposalId.toString(), teacherUserId.toString(), actorUserId.toString(),
                reason, Timestamp.from(now));
    }

    public boolean transition(
            UUID proposalId,
            ProposalWorkflowStatus expected,
            ProposalWorkflowStatus next,
            Instant now
    ) {
        return jdbcTemplate.update("""
                        UPDATE proposals
                        SET workflow_status = ?, updated_at = ?
                        WHERE id = UUID_TO_BIN(?) AND workflow_status = ?
                        """,
                next.name(), Timestamp.from(now), proposalId.toString(), expected.name()) == 1;
    }

    public void insertOfficialResponse(
            UUID id,
            UUID proposalId,
            UUID responderUserId,
            ProposalWorkflowStatus resultingStatus,
            String content,
            String decisionReason,
            String followUpPlan,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO proposal_official_responses (
                            id, proposal_id, responder_user_id, resulting_status,
                            response_content, decision_reason, follow_up_plan, created_at
                        ) VALUES (
                            UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?, ?
                        )
                        """,
                id.toString(), proposalId.toString(), responderUserId.toString(), resultingStatus.name(),
                content, decisionReason, followUpPlan, Timestamp.from(now));
    }

    public List<ProposalOfficialResponseRecord> findOfficialResponses(UUID publicId) {
        return jdbcTemplate.query("""
                        SELECT response.resulting_status, response.response_content,
                               response.decision_reason, response.follow_up_plan, response.created_at
                        FROM proposal_official_responses response
                        JOIN proposals proposal ON proposal.id = response.proposal_id
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                        ORDER BY response.created_at, response.id
                        """,
                (resultSet, rowNumber) -> new ProposalOfficialResponseRecord(
                        ProposalWorkflowStatus.valueOf(resultSet.getString("resulting_status")),
                        resultSet.getString("response_content"),
                        resultSet.getString("decision_reason"),
                        resultSet.getString("follow_up_plan"),
                        resultSet.getTimestamp("created_at").toInstant()),
                publicId.toString());
    }

    public List<AdminProposalRecord> findAdminProposals(String query, int size) {
        String queryFilter = query == null ? "" : " AND proposal.title LIKE ? ESCAPE '!'";
        Object[] parameters = query == null
                ? new Object[]{size}
                : new Object[]{"%" + escapeLike(query) + "%", size};
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(proposal.public_id) AS public_id,
                               proposal.title, proposal.workflow_status, proposal.formalized_at,
                               BIN_TO_UUID(teacher.public_id) AS teacher_public_id,
                               teacher.display_name AS teacher_display_name,
                               assignment.assigned_at
                        FROM proposals proposal
                        LEFT JOIN proposal_teacher_assignments assignment
                          ON assignment.proposal_id = proposal.id AND assignment.unassigned_at IS NULL
                        LEFT JOIN users teacher ON teacher.id = assignment.teacher_user_id
                        WHERE proposal.workflow_status <> 'GATHERING_SUPPORT'
                        """ + queryFilter + " ORDER BY proposal.formalized_at DESC, proposal.id LIMIT ?",
                (resultSet, rowNumber) -> {
                    String teacherPublicId = resultSet.getString("teacher_public_id");
                    ProposalTeacherAssignmentRecord assignment = teacherPublicId == null ? null
                            : new ProposalTeacherAssignmentRecord(
                                    UUID.fromString(teacherPublicId),
                                    resultSet.getString("teacher_display_name"),
                                    resultSet.getTimestamp("assigned_at").toInstant());
                    return new AdminProposalRecord(
                            UUID.fromString(resultSet.getString("public_id")),
                            resultSet.getString("title"),
                            ProposalWorkflowStatus.valueOf(resultSet.getString("workflow_status")),
                            resultSet.getTimestamp("formalized_at").toInstant(),
                            assignment);
                },
                parameters);
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
