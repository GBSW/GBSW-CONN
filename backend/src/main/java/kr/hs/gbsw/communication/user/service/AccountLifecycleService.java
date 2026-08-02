package kr.hs.gbsw.communication.user.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AccountRecord;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.auth.repository.AuthRepository;
import kr.hs.gbsw.communication.auth.service.RecentAuthenticationGuard;
import kr.hs.gbsw.communication.office.domain.OfficeAssignmentRecord;
import kr.hs.gbsw.communication.office.domain.OfficeType;
import kr.hs.gbsw.communication.user.domain.AccountRole;
import kr.hs.gbsw.communication.user.domain.AccountStatus;
import kr.hs.gbsw.communication.user.domain.AssignmentPeriod;
import kr.hs.gbsw.communication.user.domain.RoleAssignmentRecord;
import kr.hs.gbsw.communication.user.exception.AccountNotFoundException;
import kr.hs.gbsw.communication.user.exception.AccountStateConflictException;
import kr.hs.gbsw.communication.user.exception.AssignmentConflictException;
import kr.hs.gbsw.communication.user.exception.InvalidAssignmentPeriodException;
import kr.hs.gbsw.communication.user.repository.UserAdministrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLifecycleService {

    private final UserAdministrationRepository repository;
    private final AuthRepository authRepository;
    private final AuditLogRepository auditLogRepository;
    private final RecentAuthenticationGuard recentAuthenticationGuard;
    private final Clock clock;

    public AccountLifecycleService(
            UserAdministrationRepository repository,
            AuthRepository authRepository,
            AuditLogRepository auditLogRepository,
            RecentAuthenticationGuard recentAuthenticationGuard,
            Clock clock
    ) {
        this.repository = repository;
        this.authRepository = authRepository;
        this.auditLogRepository = auditLogRepository;
        this.recentAuthenticationGuard = recentAuthenticationGuard;
        this.clock = clock;
    }

    @Transactional
    public void suspend(AuthPrincipal actor, UUID publicId, String reason, String traceId) {
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        AccountRecord account = lockAccount(publicId);
        if (account.id().equals(actor.userId())) {
            throw new AccountStateConflictException("현재 로그인한 본인 계정은 정지할 수 없습니다.");
        }
        if (account.status() != AccountStatus.ACTIVE) {
            throw new AccountStateConflictException("활성 계정만 정지할 수 있습니다.");
        }
        repository.updateAccountStatus(account.id(), "ACTIVE", "SUSPENDED", now);
        authRepository.deleteSessions(account.loginId());
        auditLogRepository.appendWithReason(
                actor.userId(), "ADMIN_ACCOUNT_SUSPENDED", publicId, "SUCCESS", traceId, reason, now);
    }

    @Transactional
    public void reactivate(AuthPrincipal actor, UUID publicId, String reason, String traceId) {
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        AccountRecord account = lockAccount(publicId);
        if (account.status() != AccountStatus.SUSPENDED) {
            throw new AccountStateConflictException("정지된 계정만 재활성화할 수 있습니다.");
        }
        repository.updateAccountStatus(account.id(), "SUSPENDED", "ACTIVE", now);
        auditLogRepository.appendWithReason(
                actor.userId(), "ADMIN_ACCOUNT_REACTIVATED", publicId, "SUCCESS", traceId, reason, now);
    }

    @Transactional
    public AssignmentPeriod assignRole(
            AuthPrincipal actor,
            UUID publicId,
            AccountRole role,
            Instant requestedStart,
            Instant endsAt,
            String reason,
            String traceId
    ) {
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        Instant startsAt = effectiveStart(requestedStart, now);
        validatePeriod(startsAt, endsAt);
        AccountRecord account = lockAccount(publicId);
        ensureAssignableAccount(account);
        if (!repository.lockOverlappingRoleAssignments(account.id(), role, startsAt, endsAt).isEmpty()) {
            throw new AssignmentConflictException("같은 역할의 임기가 이미 해당 기간과 겹칩니다.");
        }
        repository.insertRoleAssignment(account.id(), role, actor.userId(), startsAt, endsAt, now, reason);
        if (!startsAt.isAfter(now)) {
            authRepository.deleteSessions(account.loginId());
        }
        auditLogRepository.append(actor.userId(), "ADMIN_ROLE_ASSIGNED", publicId, "SUCCESS", traceId, now);
        return new AssignmentPeriod(role.name(), startsAt, endsAt);
    }

    @Transactional
    public AssignmentPeriod endRole(
            AuthPrincipal actor,
            UUID publicId,
            AccountRole role,
            Instant requestedEnd,
            String reason,
            String traceId
    ) {
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        Instant effectiveEnd = effectiveEnd(requestedEnd, now);
        AccountRecord account = lockAccount(publicId);
        if (account.id().equals(actor.userId()) && role == AccountRole.SUPER_ADMIN) {
            throw new AssignmentConflictException("현재 로그인한 본인의 슈퍼 어드민 역할은 종료할 수 없습니다.");
        }
        List<RoleAssignmentRecord> assignments =
                repository.lockRoleAssignmentAt(account.id(), role, effectiveEnd);
        if (assignments.size() != 1) {
            throw new AssignmentConflictException("종료할 역할 임기를 하나로 특정할 수 없습니다.");
        }
        if (repository.hasOfficeAfterRoleEnd(account.id(), role, effectiveEnd)) {
            throw new AssignmentConflictException("해당 역할이 필요한 보직 임기를 먼저 종료해 주세요.");
        }
        RoleAssignmentRecord assignment = assignments.getFirst();
        repository.endRoleAssignment(assignment.id(), effectiveEnd, actor.userId(), now, reason);
        if (!effectiveEnd.isAfter(now)) {
            authRepository.deleteSessions(account.loginId());
        }
        auditLogRepository.append(actor.userId(), "ADMIN_ROLE_ENDED", publicId, "SUCCESS", traceId, now);
        return new AssignmentPeriod(role.name(), assignment.startsAt(), effectiveEnd);
    }

    @Transactional
    public AssignmentPeriod appointOffice(
            AuthPrincipal actor,
            UUID publicId,
            OfficeType office,
            Instant requestedStart,
            Instant endsAt,
            boolean replaceExistingAtStart,
            String reason,
            String traceId
    ) {
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        Instant startsAt = effectiveStart(requestedStart, now);
        validatePeriod(startsAt, endsAt);
        AccountRecord account = lockAccount(publicId);
        ensureAssignableAccount(account);
        if (!startsAt.isAfter(now) && account.status() != AccountStatus.ACTIVE) {
            throw new AccountStateConflictException("현재 보직은 활성 계정에만 임명할 수 있습니다.");
        }
        AccountRole requiredRole = requiredRole(office);
        if (!repository.roleCoversPeriod(account.id(), requiredRole, startsAt, endsAt)) {
            throw new AssignmentConflictException("보직 전체 임기를 포함하는 기본 역할이 필요합니다.");
        }

        List<OfficeAssignmentRecord> overlaps =
                repository.lockOverlappingOfficeAssignments(office, startsAt, endsAt);
        if (!overlaps.isEmpty()) {
            if (!replaceExistingAtStart || overlaps.size() != 1) {
                throw new AssignmentConflictException("해당 보직의 다른 임기와 기간이 겹칩니다.");
            }
            OfficeAssignmentRecord existing = overlaps.getFirst();
            if (!existing.startsAt().isBefore(startsAt)) {
                throw new AssignmentConflictException("같은 시각에 시작하는 기존 보직 임기는 자동 교체할 수 없습니다.");
            }
            repository.endOfficeAssignment(
                    existing.id(), startsAt, actor.userId(), now, reason);
        }

        repository.insertOfficeAssignment(
                account.id(), office, startsAt, endsAt, actor.userId(), now, reason);
        if (!startsAt.isAfter(now)) {
            authRepository.deleteSessions(account.loginId());
        }
        auditLogRepository.append(actor.userId(), "ADMIN_OFFICE_APPOINTED", publicId, "SUCCESS", traceId, now);
        return new AssignmentPeriod(office.name(), startsAt, endsAt);
    }

    @Transactional
    public AssignmentPeriod endOffice(
            AuthPrincipal actor,
            UUID publicId,
            OfficeType office,
            Instant requestedEnd,
            String reason,
            String traceId
    ) {
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        Instant effectiveEnd = effectiveEnd(requestedEnd, now);
        AccountRecord account = lockAccount(publicId);
        List<OfficeAssignmentRecord> assignments =
                repository.lockOfficeAssignmentAt(account.id(), office, effectiveEnd);
        if (assignments.size() != 1) {
            throw new AssignmentConflictException("종료할 보직 임기를 하나로 특정할 수 없습니다.");
        }
        OfficeAssignmentRecord assignment = assignments.getFirst();
        repository.endOfficeAssignment(assignment.id(), effectiveEnd, actor.userId(), now, reason);
        if (!effectiveEnd.isAfter(now)) {
            authRepository.deleteSessions(account.loginId());
        }
        auditLogRepository.append(actor.userId(), "ADMIN_OFFICE_ENDED", publicId, "SUCCESS", traceId, now);
        return new AssignmentPeriod(office.name(), assignment.startsAt(), effectiveEnd);
    }

    private AccountRecord lockAccount(UUID publicId) {
        return authRepository.lockByPublicId(publicId).orElseThrow(AccountNotFoundException::new);
    }

    private void ensureAssignableAccount(AccountRecord account) {
        if (account.status() == AccountStatus.DEACTIVATED) {
            throw new AccountStateConflictException("비활성화된 계정에는 역할이나 보직을 지정할 수 없습니다.");
        }
    }

    private Instant effectiveStart(Instant requestedStart, Instant now) {
        if (requestedStart == null) {
            return now;
        }
        if (requestedStart.isBefore(now)) {
            throw new InvalidAssignmentPeriodException();
        }
        return requestedStart;
    }

    private Instant effectiveEnd(Instant requestedEnd, Instant now) {
        if (requestedEnd == null) {
            return now;
        }
        if (requestedEnd.isBefore(now)) {
            throw new InvalidAssignmentPeriodException();
        }
        return requestedEnd;
    }

    private void validatePeriod(Instant startsAt, Instant endsAt) {
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new InvalidAssignmentPeriodException();
        }
    }

    private AccountRole requiredRole(OfficeType office) {
        return switch (office) {
            case STUDENT_AFFAIRS_TEACHER -> AccountRole.TEACHER;
            case STUDENT_COUNCIL_PRESIDENT, STUDENT_COUNCIL_VICE_PRESIDENT -> AccountRole.STUDENT;
        };
    }
}
