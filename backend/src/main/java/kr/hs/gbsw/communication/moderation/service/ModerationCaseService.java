package kr.hs.gbsw.communication.moderation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.moderation.domain.ContentReportRecord;
import kr.hs.gbsw.communication.moderation.domain.LockedContentReport;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseType;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseViewRecord;
import kr.hs.gbsw.communication.moderation.domain.ReviewerSnapshotCandidate;
import kr.hs.gbsw.communication.moderation.exception.ModerationCaseConflictException;
import kr.hs.gbsw.communication.moderation.exception.ModerationNotFoundException;
import kr.hs.gbsw.communication.moderation.exception.ReviewerConfigurationException;
import kr.hs.gbsw.communication.moderation.repository.ModerationRepository;
import kr.hs.gbsw.communication.office.domain.OfficeType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationCaseService {

    private static final Set<OfficeType> REQUIRED_OFFICES = EnumSet.allOf(OfficeType.class);

    private final ModerationRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public ModerationCaseService(
            ModerationRepository repository,
            AuditLogRepository auditLogRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ContentReportRecord> reports(AuthPrincipal actor, int size) {
        requireCurrentReviewerOffice(actor, clock.instant());
        return repository.findInboxReports(size);
    }

    @Transactional
    public ModerationCaseViewRecord create(
            AuthPrincipal actor,
            UUID reportPublicId,
            ModerationCaseType type,
            String reason,
            String traceId
    ) {
        Instant now = clock.instant();
        requireCurrentReviewerOffice(actor, now);
        LockedContentReport report = repository.lockReport(reportPublicId)
                .orElseThrow(ModerationNotFoundException::new);
        if (repository.existsCase(report.proposalId(), type)) {
            throw duplicateCase();
        }

        List<ReviewerSnapshotCandidate> reviewers = repository.lockCurrentReviewerCandidates(now);
        validateReviewers(actor, reviewers);
        UUID caseId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        try {
            repository.insertCase(
                    caseId, publicId, report, type, actor.userId(), reason.strip(), now);
            reviewers.forEach(reviewer -> repository.insertReviewerSnapshot(
                    UUID.randomUUID(), caseId, reviewer, now));
        } catch (DuplicateKeyException exception) {
            throw duplicateCase();
        }
        auditLogRepository.appendForTarget(
                actor.userId(), "MODERATION_CASE_CREATED", "MODERATION_CASE", publicId,
                "SUCCESS", traceId, now);
        return repository.findCaseForReviewer(publicId, actor.userId())
                .orElseThrow(ModerationNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<ModerationCaseViewRecord> cases(AuthPrincipal actor, int size) {
        requireReviewerAuthority(actor);
        return repository.findCasesForReviewer(actor.userId(), size);
    }

    @Transactional(readOnly = true)
    public ModerationCaseViewRecord caseDetail(AuthPrincipal actor, UUID publicId) {
        requireReviewerAuthority(actor);
        return repository.findCaseForReviewer(publicId, actor.userId())
                .orElseThrow(ModerationNotFoundException::new);
    }

    private void requireCurrentReviewerOffice(AuthPrincipal actor, Instant now) {
        boolean allowed = REQUIRED_OFFICES.stream().anyMatch(office ->
                hasRequiredRole(actor, office)
                        && actor.authorities().contains("OFFICE_" + office.name())
                        && repository.isCurrentOffice(actor.userId(), office, now));
        if (!allowed) {
            throw new AccessDeniedException("A current moderation office holder is required");
        }
    }

    private boolean hasRequiredRole(AuthPrincipal actor, OfficeType office) {
        String requiredRole = office == OfficeType.STUDENT_AFFAIRS_TEACHER
                ? "ROLE_TEACHER"
                : "ROLE_STUDENT";
        return actor.authorities().contains(requiredRole);
    }

    private void requireReviewerAuthority(AuthPrincipal actor) {
        boolean allowed = actor.authorities().contains("ROLE_STUDENT")
                || actor.authorities().contains("ROLE_TEACHER");
        if (!allowed) {
            throw new AccessDeniedException("Moderation review requires a student or teacher role");
        }
    }

    private void validateReviewers(AuthPrincipal actor, List<ReviewerSnapshotCandidate> reviewers) {
        Set<OfficeType> offices = reviewers.stream()
                .map(ReviewerSnapshotCandidate::office)
                .collect(java.util.stream.Collectors.toSet());
        long distinctUsers = reviewers.stream().map(ReviewerSnapshotCandidate::userId).distinct().count();
        boolean actorIncluded = reviewers.stream()
                .anyMatch(reviewer -> reviewer.userId().equals(actor.userId()));
        if (reviewers.size() != 3 || distinctUsers != 3
                || !offices.equals(REQUIRED_OFFICES) || !actorIncluded) {
            throw new ReviewerConfigurationException();
        }
    }

    private ModerationCaseConflictException duplicateCase() {
        return new ModerationCaseConflictException("이 제안에는 같은 유형의 심의 사건이 이미 있습니다.");
    }
}
