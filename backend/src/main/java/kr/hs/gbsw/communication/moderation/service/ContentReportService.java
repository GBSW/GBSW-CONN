package kr.hs.gbsw.communication.moderation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.moderation.domain.ContentReportResult;
import kr.hs.gbsw.communication.moderation.domain.ReportReceipt;
import kr.hs.gbsw.communication.moderation.repository.ModerationRepository;
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
    private final Clock clock;

    public ContentReportService(
            ModerationRepository repository,
            ProposalWorkflowRepository proposalRepository,
            AuditLogRepository auditLogRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.proposalRepository = proposalRepository;
        this.auditLogRepository = auditLogRepository;
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

        LockedProposal proposal = proposalRepository.lockReportableByPublicId(proposalPublicId)
                .orElseThrow(ProposalNotFoundException::new);
        if (proposal.visibilityStatus() != ProposalVisibilityStatus.VISIBLE
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
        return new ContentReportResult(new ReportReceipt(publicId, now), true);
    }

    private boolean hasActiveRole(AuthPrincipal actor, String role, Instant now) {
        return actor.authorities().contains("ROLE_" + role)
                && proposalRepository.isActiveRole(actor.userId(), role, now);
    }
}
