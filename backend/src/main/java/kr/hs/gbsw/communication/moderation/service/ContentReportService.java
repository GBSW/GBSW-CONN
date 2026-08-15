package kr.hs.gbsw.communication.moderation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.moderation.domain.ContentReportResult;
import kr.hs.gbsw.communication.moderation.domain.ReportReceipt;
import kr.hs.gbsw.communication.moderation.repository.ModerationRepository;
import kr.hs.gbsw.communication.proposal.config.ProposalProperties;
import kr.hs.gbsw.communication.proposal.domain.LockedProposal;
import kr.hs.gbsw.communication.proposal.domain.ProposalVisibilityStatus;
import kr.hs.gbsw.communication.proposal.domain.ProposalWorkflowStatus;
import kr.hs.gbsw.communication.proposal.exception.ProposalNotFoundException;
import kr.hs.gbsw.communication.proposal.repository.ProposalWorkflowRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentReportService {

    private final ModerationRepository repository;
    private final ProposalWorkflowRepository proposalRepository;
    private final AuditLogRepository auditLogRepository;
    private final ProposalProperties properties;
    private final Clock clock;

    public ContentReportService(
            ModerationRepository repository,
            ProposalWorkflowRepository proposalRepository,
            AuditLogRepository auditLogRepository,
            ProposalProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.proposalRepository = proposalRepository;
        this.auditLogRepository = auditLogRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ContentReportResult report(
            AuthPrincipal actor,
            UUID proposalPublicId,
            String reason,
            String traceId
    ) {
        Instant now = clock.instant();
        boolean student = hasActiveRole(actor, "STUDENT", now);
        boolean teacher = hasActiveRole(actor, "TEACHER", now);
        if (!student && !teacher) {
            throw new AccessDeniedException("Content reporting requires a current student or teacher role");
        }

        LockedProposal proposal = proposalRepository.lockByPublicId(proposalPublicId)
                .orElseThrow(ProposalNotFoundException::new);
        // 임시 가림은 확정 결정이 아니므로 신고를 계속 받는다. 신고가 임계값을 함께
        // 넘긴 동시 요청에서 뒤늦은 신고가 유실되지 않고, 심의자가 보는 신고 수도
        // 실제 접수량을 반영한다. 심의로 확정 공개 제한된 제안만 거절한다.
        if (proposal.visibilityStatus() == ProposalVisibilityStatus.HIDDEN_BY_DECISION
                || (!student && proposal.workflowStatus() == ProposalWorkflowStatus.GATHERING_SUPPORT)) {
            throw new ProposalNotFoundException();
        }

        UUID publicId = UUID.randomUUID();
        try {
            repository.insertReport(
                    UUID.randomUUID(), publicId, proposal.id(), actor.userId(), reason.strip(), now);
        } catch (DuplicateKeyException exception) {
            ReportReceipt existing = repository.findReportReceipt(proposal.id(), actor.userId())
                    .orElseThrow(() -> exception);
            return new ContentReportResult(existing, false);
        }
        auditLogRepository.appendForTarget(
                actor.userId(), "CONTENT_REPORT_CREATED", "PROPOSAL", proposalPublicId,
                "SUCCESS", traceId, now);
        applyReportThreshold(proposal, proposalPublicId, traceId, now);
        return new ContentReportResult(new ReportReceipt(publicId, now), true);
    }

    /**
     * 신고가 임계값에 이르면 일반 사용자에게서 제안을 임시로 가린다.
     *
     * 이 가림은 되돌릴 수 있는 임시 상태이며, 원문을 지우거나 심의 결과를 대신하지
     * 않는다. 확산을 늦출 뿐 최종 판단은 3인 심의가 한다.
     *
     * 제안 행은 이 트랜잭션 시작에서 이미 잠겨 있으므로 같은 순간에 들어온 신고가
     * 임계값을 함께 넘겨도 가림은 한 번만 적용된다. 가림을 적용한 요청만 감사
     * 기록을 남긴다.
     */
    private void applyReportThreshold(
            LockedProposal proposal,
            UUID proposalPublicId,
            String traceId,
            Instant now
    ) {
        if (repository.countReports(proposal.id()) < properties.reportHideThreshold()) {
            return;
        }
        if (!repository.restrictProposalByReportThreshold(proposal.id(), now)) {
            return;
        }
        auditLogRepository.appendForTarget(
                null, "PROPOSAL_RESTRICTED_BY_REPORTS", "PROPOSAL", proposalPublicId,
                "SUCCESS", traceId, now);
    }

    private boolean hasActiveRole(AuthPrincipal actor, String role, Instant now) {
        return actor.authorities().contains("ROLE_" + role)
                && proposalRepository.isActiveRole(actor.userId(), role, now);
    }
}
