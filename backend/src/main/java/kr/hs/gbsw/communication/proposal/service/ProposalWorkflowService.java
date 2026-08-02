package kr.hs.gbsw.communication.proposal.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.proposal.domain.LockedProposal;
import kr.hs.gbsw.communication.proposal.domain.ProposalWorkflowStatus;
import kr.hs.gbsw.communication.proposal.exception.ProposalAssignmentRequiredException;
import kr.hs.gbsw.communication.proposal.exception.ProposalNotFoundException;
import kr.hs.gbsw.communication.proposal.exception.ProposalStateConflictException;
import kr.hs.gbsw.communication.proposal.repository.ProposalRepository;
import kr.hs.gbsw.communication.proposal.repository.ProposalWorkflowRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalWorkflowService {

    private final ProposalWorkflowRepository workflowRepository;
    private final ProposalRepository proposalRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public ProposalWorkflowService(
            ProposalWorkflowRepository workflowRepository,
            ProposalRepository proposalRepository,
            AuditLogRepository auditLogRepository,
            Clock clock
    ) {
        this.workflowRepository = workflowRepository;
        this.proposalRepository = proposalRepository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional
    public ProposalWorkflowStatus startReview(
            AuthPrincipal actor,
            UUID publicId,
            String reason,
            String traceId
    ) {
        return transitionWithoutResponse(
                actor, publicId, ProposalWorkflowStatus.FORMAL_AGENDA,
                ProposalWorkflowStatus.UNDER_REVIEW, reason, "PROPOSAL_REVIEW_STARTED", traceId);
    }

    @Transactional
    public ProposalWorkflowStatus resumeReview(
            AuthPrincipal actor,
            UUID publicId,
            String reason,
            String traceId
    ) {
        return transitionWithoutResponse(
                actor, publicId, ProposalWorkflowStatus.ON_HOLD,
                ProposalWorkflowStatus.UNDER_REVIEW, reason, "PROPOSAL_REVIEW_RESUMED", traceId);
    }

    @Transactional
    public ProposalWorkflowStatus accept(
            AuthPrincipal actor,
            UUID publicId,
            String content,
            String decisionReason,
            String followUpPlan,
            String traceId
    ) {
        return transitionWithResponse(
                actor, publicId, ProposalWorkflowStatus.UNDER_REVIEW, ProposalWorkflowStatus.ACCEPTED,
                content, decisionReason, followUpPlan, "PROPOSAL_ACCEPTED", traceId);
    }

    @Transactional
    public ProposalWorkflowStatus hold(
            AuthPrincipal actor,
            UUID publicId,
            String content,
            String decisionReason,
            String followUpPlan,
            String traceId
    ) {
        return transitionWithResponse(
                actor, publicId, ProposalWorkflowStatus.UNDER_REVIEW, ProposalWorkflowStatus.ON_HOLD,
                content, decisionReason, followUpPlan, "PROPOSAL_PUT_ON_HOLD", traceId);
    }

    @Transactional
    public ProposalWorkflowStatus reject(
            AuthPrincipal actor,
            UUID publicId,
            String content,
            String decisionReason,
            String followUpPlan,
            String traceId
    ) {
        return transitionWithResponse(
                actor, publicId, ProposalWorkflowStatus.UNDER_REVIEW, ProposalWorkflowStatus.REJECTED,
                content, decisionReason, followUpPlan, "PROPOSAL_REJECTED", traceId);
    }

    @Transactional
    public ProposalWorkflowStatus startExecution(
            AuthPrincipal actor,
            UUID publicId,
            String content,
            String decisionReason,
            String followUpPlan,
            String traceId
    ) {
        return transitionWithResponse(
                actor, publicId, ProposalWorkflowStatus.ACCEPTED, ProposalWorkflowStatus.IN_PROGRESS,
                content, decisionReason, followUpPlan, "PROPOSAL_EXECUTION_STARTED", traceId);
    }

    @Transactional
    public ProposalWorkflowStatus completeExecution(
            AuthPrincipal actor,
            UUID publicId,
            String content,
            String decisionReason,
            String followUpPlan,
            String traceId
    ) {
        return transitionWithResponse(
                actor, publicId, ProposalWorkflowStatus.IN_PROGRESS, ProposalWorkflowStatus.COMPLETED,
                content, decisionReason, followUpPlan, "PROPOSAL_EXECUTION_COMPLETED", traceId);
    }

    private ProposalWorkflowStatus transitionWithoutResponse(
            AuthPrincipal actor,
            UUID publicId,
            ProposalWorkflowStatus expected,
            ProposalWorkflowStatus next,
            String reason,
            String eventType,
            String traceId
    ) {
        Instant now = clock.instant();
        LockedProposal proposal = requireAssignedTeacher(actor, publicId, now);
        requireStatus(proposal, expected);
        String normalizedReason = reason.strip();
        applyTransition(proposal, actor, expected, next, normalizedReason, eventType, traceId, now);
        return next;
    }

    private ProposalWorkflowStatus transitionWithResponse(
            AuthPrincipal actor,
            UUID publicId,
            ProposalWorkflowStatus expected,
            ProposalWorkflowStatus next,
            String content,
            String decisionReason,
            String followUpPlan,
            String eventType,
            String traceId
    ) {
        Instant now = clock.instant();
        LockedProposal proposal = requireAssignedTeacher(actor, publicId, now);
        requireStatus(proposal, expected);
        String normalizedContent = content.strip();
        String normalizedReason = decisionReason.strip();
        String normalizedFollowUpPlan = followUpPlan == null || followUpPlan.isBlank()
                ? null : followUpPlan.strip();
        applyTransition(proposal, actor, expected, next, normalizedReason, eventType, traceId, now);
        workflowRepository.insertOfficialResponse(
                UUID.randomUUID(), proposal.id(), actor.userId(), next,
                normalizedContent, normalizedReason, normalizedFollowUpPlan, now);
        return next;
    }

    private LockedProposal requireAssignedTeacher(AuthPrincipal actor, UUID publicId, Instant now) {
        if (!actor.authorities().contains("ROLE_TEACHER")) {
            throw new AccessDeniedException("Teacher role is required");
        }
        LockedProposal proposal = workflowRepository.lockByPublicId(publicId)
                .orElseThrow(ProposalNotFoundException::new);
        if (!workflowRepository.isCurrentAssignedTeacher(proposal.id(), actor.userId(), now)) {
            throw new ProposalAssignmentRequiredException();
        }
        return proposal;
    }

    private void requireStatus(LockedProposal proposal, ProposalWorkflowStatus expected) {
        if (proposal.workflowStatus() != expected) {
            throw new ProposalStateConflictException(
                    expected.name() + " 상태에서만 이 작업을 수행할 수 있습니다.");
        }
    }

    private void applyTransition(
            LockedProposal proposal,
            AuthPrincipal actor,
            ProposalWorkflowStatus expected,
            ProposalWorkflowStatus next,
            String reason,
            String eventType,
            String traceId,
            Instant now
    ) {
        if (!workflowRepository.transition(proposal.id(), expected, next, now)) {
            throw new ProposalStateConflictException("다른 요청이 먼저 상태를 변경했습니다. 새로고침 후 다시 시도해 주세요.");
        }
        proposalRepository.insertStatusHistory(
                proposal.id(), expected, next, actor.userId(), null, reason, now);
        auditLogRepository.appendForTarget(
                actor.userId(), eventType, "PROPOSAL", proposal.publicId(),
                "SUCCESS", traceId, now);
    }
}
