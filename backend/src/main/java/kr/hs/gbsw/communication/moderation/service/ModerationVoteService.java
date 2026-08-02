package kr.hs.gbsw.communication.moderation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.moderation.domain.LockedModerationCase;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseStatus;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseType;
import kr.hs.gbsw.communication.moderation.domain.ModerationVoteDecision;
import kr.hs.gbsw.communication.moderation.exception.ModerationCaseConflictException;
import kr.hs.gbsw.communication.moderation.exception.ModerationNotFoundException;
import kr.hs.gbsw.communication.moderation.repository.ModerationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationVoteService {

    private final ModerationRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public ModerationVoteService(
            ModerationRepository repository,
            AuditLogRepository auditLogRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional
    public ModerationCaseStatus vote(
            AuthPrincipal actor,
            UUID casePublicId,
            ModerationVoteDecision decision,
            String reason,
            String traceId
    ) {
        requireReviewerAuthority(actor);
        Instant now = clock.instant();
        LockedModerationCase moderationCase = repository.lockCaseForReviewer(casePublicId, actor.userId())
                .orElseThrow(ModerationNotFoundException::new);
        if (moderationCase.status() != ModerationCaseStatus.PENDING) {
            throw new ModerationCaseConflictException("이미 결정된 심의 사건입니다.");
        }
        if (repository.hasVote(moderationCase.reviewerSnapshotId())) {
            throw new ModerationCaseConflictException("이미 이 심의 사건에 의결했습니다.");
        }

        repository.insertVote(
                UUID.randomUUID(), moderationCase.reviewerSnapshotId(), decision, reason.strip(), now);
        ModerationRepository.VoteCounts counts = repository.countVotes(moderationCase.id());
        ModerationCaseStatus status = ModerationCaseStatus.PENDING;
        if (counts.rejections() > 0) {
            status = ModerationCaseStatus.REJECTED;
        } else if (counts.total() == 3 && counts.approvals() == 3) {
            status = ModerationCaseStatus.APPROVED;
        }

        auditLogRepository.appendForTarget(
                actor.userId(), "MODERATION_VOTE_" + decision.name(), "MODERATION_CASE", casePublicId,
                "SUCCESS", traceId, now);
        if (status != ModerationCaseStatus.PENDING) {
            repository.decideCase(moderationCase.id(), status, now);
            if (status == ModerationCaseStatus.APPROVED
                    && moderationCase.type() == ModerationCaseType.CONTENT_VISIBILITY) {
                repository.hideProposalByDecision(moderationCase.proposalId(), moderationCase.id(), now);
                auditLogRepository.appendForTarget(
                        actor.userId(), "PROPOSAL_HIDDEN_BY_DECISION", "PROPOSAL",
                        moderationCase.proposalPublicId(), "SUCCESS", traceId, now);
            }
            auditLogRepository.appendForTarget(
                    actor.userId(), "MODERATION_CASE_" + status.name(), "MODERATION_CASE", casePublicId,
                    "SUCCESS", traceId, now);
        }
        return status;
    }

    private void requireReviewerAuthority(AuthPrincipal actor) {
        if (!actor.authorities().contains("ROLE_STUDENT")
                && !actor.authorities().contains("ROLE_TEACHER")) {
            throw new AccessDeniedException("Moderation review requires a student or teacher role");
        }
    }
}
