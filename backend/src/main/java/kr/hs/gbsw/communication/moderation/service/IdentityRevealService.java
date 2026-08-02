package kr.hs.gbsw.communication.moderation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.auth.domain.ThrottleOperation;
import kr.hs.gbsw.communication.auth.exception.AuthenticationThrottledException;
import kr.hs.gbsw.communication.auth.service.AuthenticationThrottleService;
import kr.hs.gbsw.communication.auth.service.RecentAuthenticationGuard;
import kr.hs.gbsw.communication.moderation.domain.IdentityRevealResult;
import kr.hs.gbsw.communication.moderation.domain.LockedModerationCase;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseStatus;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseType;
import kr.hs.gbsw.communication.moderation.domain.ProtectedProposalIdentity;
import kr.hs.gbsw.communication.moderation.domain.RevealedIdentity;
import kr.hs.gbsw.communication.moderation.exception.IdentityRevealUnavailableException;
import kr.hs.gbsw.communication.moderation.exception.ModerationNotFoundException;
import kr.hs.gbsw.communication.moderation.repository.ModerationRepository;
import kr.hs.gbsw.communication.office.domain.OfficeType;
import kr.hs.gbsw.communication.proposal.service.ProposalIdentityCipher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityRevealService {

    private final ModerationRepository repository;
    private final RecentAuthenticationGuard recentAuthenticationGuard;
    private final AuthenticationThrottleService throttleService;
    private final ProposalIdentityCipher identityCipher;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public IdentityRevealService(
            ModerationRepository repository,
            RecentAuthenticationGuard recentAuthenticationGuard,
            AuthenticationThrottleService throttleService,
            ProposalIdentityCipher identityCipher,
            AuditLogRepository auditLogRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.recentAuthenticationGuard = recentAuthenticationGuard;
        this.throttleService = throttleService;
        this.identityCipher = identityCipher;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = {
            IdentityRevealUnavailableException.class,
            ModerationNotFoundException.class,
            AuthenticationThrottledException.class
    })
    public IdentityRevealResult reveal(
            AuthPrincipal actor,
            UUID casePublicId,
            String reason,
            String remoteAddress,
            String traceId
    ) {
        if (!actor.authorities().contains("ROLE_TEACHER")) {
            throw new AccessDeniedException("Identity reveal requires a teacher role");
        }
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        try {
            throttleService.assertAllowed(
                    ThrottleOperation.IDENTITY_REVEAL, actor.loginId(), remoteAddress, now);
            LockedModerationCase moderationCase = repository.lockCaseForReviewer(casePublicId, actor.userId())
                    .orElseThrow(ModerationNotFoundException::new);
            if (moderationCase.type() != ModerationCaseType.IDENTITY_REVEAL
                    || moderationCase.status() != ModerationCaseStatus.APPROVED
                    || moderationCase.viewerOffice() != OfficeType.STUDENT_AFFAIRS_TEACHER) {
                throw new IdentityRevealUnavailableException(
                        "승인된 신원 확인 사건의 학생부장만 신원을 확인할 수 있습니다.");
            }
            if (repository.hasIdentityReveal(moderationCase.id())) {
                throw new IdentityRevealUnavailableException("이 사건의 신원은 이미 한 번 확인되었습니다.");
            }

            ProtectedProposalIdentity protectedIdentity = repository.findProtectedIdentity(moderationCase.id())
                    .orElseThrow(this::unavailable);
            RevealedIdentity identity;
            try {
                UUID userId = identityCipher.decrypt(
                        protectedIdentity.proposalPublicId(), protectedIdentity.identity());
                identity = repository.findIdentity(userId).orElseThrow(this::unavailable);
            } catch (IllegalStateException exception) {
                throw unavailable();
            }

            repository.insertIdentityReveal(
                    UUID.randomUUID(), moderationCase.id(), actor.userId(), reason.strip(), now);
            throttleService.clear(ThrottleOperation.IDENTITY_REVEAL, actor.loginId(), remoteAddress);
            auditLogRepository.appendForTarget(
                    actor.userId(), "PROPOSAL_IDENTITY_REVEALED", "MODERATION_CASE", casePublicId,
                    "SUCCESS", traceId, now);
            return new IdentityRevealResult(identity, now);
        } catch (ModerationNotFoundException | IdentityRevealUnavailableException exception) {
            throttleService.recordFailure(
                    ThrottleOperation.IDENTITY_REVEAL, actor.loginId(), remoteAddress, now);
            auditLogRepository.appendForTarget(
                    actor.userId(), "PROPOSAL_IDENTITY_REVEAL_FAILED", "MODERATION_CASE", casePublicId,
                    "FAILURE", traceId, now);
            throw exception;
        } catch (AuthenticationThrottledException exception) {
            auditLogRepository.appendForTarget(
                    actor.userId(), "PROPOSAL_IDENTITY_REVEAL_BLOCKED", "MODERATION_CASE", casePublicId,
                    "BLOCKED", traceId, now);
            throw exception;
        }
    }

    private IdentityRevealUnavailableException unavailable() {
        return new IdentityRevealUnavailableException("보호된 작성자 정보를 확인할 수 없습니다.");
    }
}
