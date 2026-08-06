package kr.hs.gbsw.communication.proposal.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.proposal.config.ProposalProperties;
import kr.hs.gbsw.communication.proposal.domain.AuthorVisibility;
import kr.hs.gbsw.communication.proposal.domain.EncryptedProposalIdentity;
import kr.hs.gbsw.communication.proposal.domain.LockedProposal;
import kr.hs.gbsw.communication.proposal.domain.ProposalFeedScope;
import kr.hs.gbsw.communication.proposal.domain.ProposalCommentRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalSort;
import kr.hs.gbsw.communication.proposal.domain.ProposalStatusHistoryRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalViewRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalWorkflowStatus;
import kr.hs.gbsw.communication.proposal.domain.SupportResult;
import kr.hs.gbsw.communication.proposal.dto.response.ProposalDetailResponse;
import kr.hs.gbsw.communication.proposal.dto.response.ProposalCommentResponse;
import kr.hs.gbsw.communication.proposal.dto.response.ProposalPageResponse;
import kr.hs.gbsw.communication.proposal.exception.ProposalNotFoundException;
import kr.hs.gbsw.communication.proposal.exception.ProposalCommentNotFoundException;
import kr.hs.gbsw.communication.proposal.exception.ProposalStateConflictException;
import kr.hs.gbsw.communication.proposal.exception.StudentRoleRequiredException;
import kr.hs.gbsw.communication.proposal.exception.SupportWithdrawalClosedException;
import kr.hs.gbsw.communication.proposal.repository.ProposalRepository;
import kr.hs.gbsw.communication.proposal.repository.ProposalWorkflowRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalService {

    private static final String INITIAL_STATUS_REASON = "제안 등록과 작성자 자동 동의";
    private static final String FORMALIZATION_REASON = "유효 동의 임계값 도달";

    private final ProposalRepository repository;
    private final ProposalIdentityCipher identityCipher;
    private final ProposalProperties properties;
    private final ProposalWorkflowRepository workflowRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public ProposalService(
            ProposalRepository repository,
            ProposalIdentityCipher identityCipher,
            ProposalProperties properties,
            ProposalWorkflowRepository workflowRepository,
            AuditLogRepository auditLogRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.identityCipher = identityCipher;
        this.properties = properties;
        this.workflowRepository = workflowRepository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional
    public ProposalDetailResponse create(
            AuthPrincipal actor,
            String title,
            String content,
            AuthorVisibility authorVisibility,
            String traceId
    ) {
        Instant now = clock.instant();
        requireStudentAuthority(actor);
        requireActiveStudent(actor, now);
        UUID proposalId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        String normalizedTitle = title.strip();
        String normalizedContent = content.strip();
        String publicAuthorName = authorVisibility == AuthorVisibility.NAMED ? actor.displayName() : null;
        EncryptedProposalIdentity identity = identityCipher.encrypt(publicId, actor.userId());

        repository.insertProposal(
                proposalId, publicId, normalizedTitle, normalizedContent,
                authorVisibility, publicAuthorName, now);
        repository.insertIdentity(proposalId, identity, now);
        repository.insertSupport(proposalId, actor.userId(), now);
        repository.insertStatusHistory(
                proposalId, null, ProposalWorkflowStatus.GATHERING_SUPPORT,
                null, 1, INITIAL_STATUS_REASON, now);
        auditLogRepository.appendForTarget(
                null, "PROPOSAL_CREATED", "PROPOSAL", publicId,
                "SUCCESS", traceId, now);

        ProposalViewRecord created = new ProposalViewRecord(
                publicId, normalizedTitle, normalizedContent, authorVisibility, publicAuthorName,
                ProposalWorkflowStatus.GATHERING_SUPPORT,
                kr.hs.gbsw.communication.proposal.domain.ProposalVisibilityStatus.VISIBLE,
                1, true, null, null, now);
        ProposalStatusHistoryRecord initialHistory = new ProposalStatusHistoryRecord(
                null, ProposalWorkflowStatus.GATHERING_SUPPORT, 1, INITIAL_STATUS_REASON, now);
        return ProposalDetailResponse.from(created, List.of(initialHistory), properties.supportThreshold());
    }

    @Transactional(readOnly = true)
    public ProposalPageResponse list(
            AuthPrincipal viewer,
            ProposalFeedScope scope,
            ProposalSort sort,
            String query,
            int page,
            int size
    ) {
        requireReader(viewer);
        String normalizedQuery = query == null || query.isBlank() ? null : query.strip();
        Instant now = clock.instant();
        long offset = (long) page * size;
        return ProposalPageResponse.from(
                repository.findFeed(
                        viewer.userId(), scope, normalizedQuery, sort,
                        now, size, offset),
                properties.supportThreshold(),
                page,
                size,
                repository.countFeed(scope, normalizedQuery));
    }

    @Transactional(readOnly = true)
    public ProposalDetailResponse get(AuthPrincipal viewer, UUID publicId) {
        requireReader(viewer);
        Instant now = clock.instant();
        ProposalViewRecord proposal = repository.findDetail(publicId, viewer.userId(), now)
                .orElseThrow(ProposalNotFoundException::new);
        boolean viewerCanManage = viewer.authorities().contains("ROLE_TEACHER")
                && workflowRepository.isCurrentAssignedTeacherByPublicId(publicId, viewer.userId(), now);
        boolean viewerCanEdit = proposal.workflowStatus() == ProposalWorkflowStatus.GATHERING_SUPPORT
                && viewer.authorities().contains("ROLE_STUDENT")
                && isAuthor(viewer, publicId);
        return ProposalDetailResponse.from(
                proposal,
                repository.findStatusHistory(publicId),
                workflowRepository.findOfficialResponses(publicId),
                viewerCanManage,
                viewerCanEdit,
                properties.supportThreshold());
    }

    @Transactional
    public ProposalDetailResponse update(
            AuthPrincipal actor,
            UUID publicId,
            String title,
            String content,
            String traceId
    ) {
        Instant now = clock.instant();
        requireStudentAuthority(actor);
        requireActiveStudent(actor, now);
        LockedProposal proposal = repository.lockVisibleByPublicId(publicId)
                .orElseThrow(ProposalNotFoundException::new);
        requireAuthor(actor, publicId);
        requireGatheringSupport(proposal, "정식 안건이 된 제안은 수정할 수 없습니다.");
        if (!repository.updateDraft(proposal.id(), title.strip(), content.strip(), now)) {
            throw new ProposalStateConflictException("제안 상태가 변경되어 수정할 수 없습니다.");
        }
        auditLogRepository.appendForTarget(
                null, "PROPOSAL_UPDATED", "PROPOSAL", publicId,
                "SUCCESS", traceId, now);
        return get(actor, publicId);
    }

    @Transactional
    public void withdraw(AuthPrincipal actor, UUID publicId, String traceId) {
        Instant now = clock.instant();
        requireStudentAuthority(actor);
        requireActiveStudent(actor, now);
        LockedProposal proposal = repository.lockVisibleByPublicId(publicId)
                .orElseThrow(ProposalNotFoundException::new);
        requireAuthor(actor, publicId);
        requireGatheringSupport(proposal, "정식 안건이 된 제안은 철회할 수 없습니다.");
        if (!repository.withdrawDraft(proposal.id(), now)) {
            throw new ProposalStateConflictException("제안 상태가 변경되어 철회할 수 없습니다.");
        }
        auditLogRepository.appendForTarget(
                null, "PROPOSAL_WITHDRAWN", "PROPOSAL", publicId,
                "SUCCESS", traceId, now);
    }

    @Transactional(readOnly = true)
    public List<ProposalCommentResponse> listComments(AuthPrincipal viewer, UUID publicId) {
        get(viewer, publicId);
        return repository.findComments(publicId, viewer.userId()).stream()
                .map(ProposalCommentResponse::from)
                .toList();
    }

    @Transactional
    public ProposalCommentResponse createComment(
            AuthPrincipal actor,
            UUID publicId,
            String content,
            String traceId
    ) {
        Instant now = clock.instant();
        requireStudentAuthority(actor);
        requireActiveStudent(actor, now);
        LockedProposal proposal = repository.lockVisibleByPublicId(publicId)
                .orElseThrow(ProposalNotFoundException::new);
        UUID commentPublicId = UUID.randomUUID();
        ProposalCommentRecord comment = repository.insertComment(
                proposal.id(), commentPublicId, actor.userId(), actor.displayName(), content.strip(), now);
        auditLogRepository.appendForTarget(
                actor.userId(), "PROPOSAL_COMMENT_CREATED", "PROPOSAL_COMMENT", commentPublicId,
                "SUCCESS", traceId, now);
        return ProposalCommentResponse.from(comment);
    }

    @Transactional
    public void deleteComment(
            AuthPrincipal actor,
            UUID proposalPublicId,
            UUID commentPublicId,
            String traceId
    ) {
        Instant now = clock.instant();
        requireStudentAuthority(actor);
        requireActiveStudent(actor, now);
        get(actor, proposalPublicId);
        if (!repository.deleteComment(proposalPublicId, commentPublicId, actor.userId(), now)) {
            throw new ProposalCommentNotFoundException();
        }
        auditLogRepository.appendForTarget(
                actor.userId(), "PROPOSAL_COMMENT_DELETED", "PROPOSAL_COMMENT", commentPublicId,
                "SUCCESS", traceId, now);
    }

    @Transactional
    public SupportResult support(AuthPrincipal actor, UUID publicId, String traceId) {
        requireStudentAuthority(actor);
        Instant now = clock.instant();
        LockedProposal proposal = repository.lockVisibleByPublicId(publicId)
                .orElseThrow(ProposalNotFoundException::new);
        requireActiveStudent(actor, now);
        repository.insertSupport(proposal.id(), actor.userId(), now);
        int supportCount = repository.countValidSupports(proposal.id(), now);
        ProposalWorkflowStatus nextStatus = proposal.workflowStatus();
        boolean justFormalized = false;

        if (proposal.workflowStatus() == ProposalWorkflowStatus.GATHERING_SUPPORT
                && supportCount >= properties.supportThreshold()) {
            justFormalized = repository.formalize(proposal.id(), supportCount, now);
            if (justFormalized) {
                nextStatus = ProposalWorkflowStatus.FORMAL_AGENDA;
                repository.insertStatusHistory(
                        proposal.id(), ProposalWorkflowStatus.GATHERING_SUPPORT,
                        ProposalWorkflowStatus.FORMAL_AGENDA, null,
                        supportCount, FORMALIZATION_REASON, now);
                repository.insertFormalAgendaNotification(proposal.id(), now);
                auditLogRepository.appendForTarget(
                        null, "PROPOSAL_FORMALIZED", "PROPOSAL", publicId,
                        "SUCCESS", traceId, now);
            }
        }
        return new SupportResult(true, supportCount, nextStatus, justFormalized);
    }

    @Transactional
    public SupportResult withdrawSupport(AuthPrincipal actor, UUID publicId) {
        requireStudentAuthority(actor);
        Instant now = clock.instant();
        LockedProposal proposal = repository.lockVisibleByPublicId(publicId)
                .orElseThrow(ProposalNotFoundException::new);
        requireActiveStudent(actor, now);
        if (proposal.workflowStatus() != ProposalWorkflowStatus.GATHERING_SUPPORT) {
            throw new SupportWithdrawalClosedException();
        }
        repository.deleteSupport(proposal.id(), actor.userId());
        int supportCount = repository.countValidSupports(proposal.id(), now);
        return new SupportResult(false, supportCount, proposal.workflowStatus(), false);
    }

    public int supportThreshold() {
        return properties.supportThreshold();
    }

    private boolean isAuthor(AuthPrincipal actor, UUID publicId) {
        EncryptedProposalIdentity identity = repository.findEncryptedIdentity(publicId)
                .orElseThrow(ProposalNotFoundException::new);
        return identityCipher.decrypt(publicId, identity).equals(actor.userId());
    }

    private void requireAuthor(AuthPrincipal actor, UUID publicId) {
        if (!isAuthor(actor, publicId)) {
            throw new AccessDeniedException("Only the proposal author may change it");
        }
    }

    private void requireGatheringSupport(LockedProposal proposal, String message) {
        if (proposal.workflowStatus() != ProposalWorkflowStatus.GATHERING_SUPPORT) {
            throw new ProposalStateConflictException(message);
        }
    }

    private void requireStudentAuthority(AuthPrincipal principal) {
        if (!principal.authorities().contains("ROLE_STUDENT")) {
            throw new StudentRoleRequiredException();
        }
    }

    private void requireActiveStudent(AuthPrincipal principal, Instant now) {
        if (!repository.isActiveStudent(principal.userId(), now)) {
            throw new StudentRoleRequiredException();
        }
    }

    private void requireReader(AuthPrincipal principal) {
        if (principal.authorities().contains("ROLE_STUDENT")
                || principal.authorities().contains("ROLE_TEACHER")) {
            return;
        }
        throw new AccessDeniedException("Proposal feed requires a student or teacher role");
    }
}
