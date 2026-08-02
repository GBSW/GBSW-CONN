package kr.hs.gbsw.communication.proposal.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.auth.service.RecentAuthenticationGuard;
import kr.hs.gbsw.communication.proposal.domain.AdminProposalRecord;
import kr.hs.gbsw.communication.proposal.domain.CurrentProposalTeacherAssignment;
import kr.hs.gbsw.communication.proposal.domain.EligibleProposalTeacher;
import kr.hs.gbsw.communication.proposal.domain.LockedProposal;
import kr.hs.gbsw.communication.proposal.domain.ProposalTeacherAssignmentRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalWorkflowStatus;
import kr.hs.gbsw.communication.proposal.exception.ProposalNotFoundException;
import kr.hs.gbsw.communication.proposal.exception.ProposalStateConflictException;
import kr.hs.gbsw.communication.proposal.exception.ProposalTeacherNotEligibleException;
import kr.hs.gbsw.communication.proposal.repository.ProposalWorkflowRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalAssignmentService {

    private final ProposalWorkflowRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final RecentAuthenticationGuard recentAuthenticationGuard;
    private final Clock clock;

    public ProposalAssignmentService(
            ProposalWorkflowRepository repository,
            AuditLogRepository auditLogRepository,
            RecentAuthenticationGuard recentAuthenticationGuard,
            Clock clock
    ) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.recentAuthenticationGuard = recentAuthenticationGuard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminProposalRecord> list(AuthPrincipal actor, String query, int size) {
        requireSuperAdmin(actor);
        String normalizedQuery = query == null || query.isBlank() ? null : query.strip();
        return repository.findAdminProposals(normalizedQuery, size);
    }

    @Transactional(readOnly = true)
    public List<EligibleProposalTeacher> teachers(AuthPrincipal actor, String query, int size) {
        requireSuperAdmin(actor);
        String normalizedQuery = query == null || query.isBlank() ? null : query.strip();
        return repository.findEligibleTeachers(normalizedQuery, clock.instant(), size);
    }

    @Transactional
    public ProposalTeacherAssignmentRecord assign(
            AuthPrincipal actor,
            UUID proposalPublicId,
            UUID teacherPublicId,
            String reason,
            String traceId
    ) {
        requireSuperAdmin(actor);
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        LockedProposal proposal = repository.lockByPublicId(proposalPublicId)
                .orElseThrow(ProposalNotFoundException::new);
        if (!repository.isActiveRole(actor.userId(), "SUPER_ADMIN", now)) {
            throw new AccessDeniedException("Current super administrator role is required");
        }
        if (proposal.workflowStatus() == ProposalWorkflowStatus.GATHERING_SUPPORT
                || proposal.workflowStatus() == ProposalWorkflowStatus.REJECTED
                || proposal.workflowStatus() == ProposalWorkflowStatus.COMPLETED) {
            throw new ProposalStateConflictException("처리 중인 정식 안건에만 담당 교사를 지정할 수 있습니다.");
        }
        EligibleProposalTeacher teacher = repository.findEligibleTeacher(teacherPublicId, now)
                .orElseThrow(ProposalTeacherNotEligibleException::new);
        CurrentProposalTeacherAssignment current = repository.findCurrentAssignment(proposal.id()).orElse(null);
        if (current != null && current.teacherUserId().equals(teacher.id())) {
            return current.toPublicRecord();
        }

        String normalizedReason = reason.strip();
        if (current != null) {
            repository.endCurrentAssignment(proposal.id(), actor.userId(), normalizedReason, now);
        }
        repository.insertAssignment(
                UUID.randomUUID(), proposal.id(), teacher.id(), actor.userId(), normalizedReason, now);
        auditLogRepository.appendForTarget(
                actor.userId(), "PROPOSAL_TEACHER_ASSIGNED", "PROPOSAL", proposalPublicId,
                "SUCCESS", traceId, now);
        return new ProposalTeacherAssignmentRecord(teacher.publicId(), teacher.displayName(), now);
    }

    private void requireSuperAdmin(AuthPrincipal actor) {
        if (!actor.authorities().contains("ROLE_SUPER_ADMIN")) {
            throw new AccessDeniedException("Super administrator role is required");
        }
    }
}
