package kr.hs.gbsw.communication.governance.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.delivery.CredentialDeliveryCommand;
import kr.hs.gbsw.communication.auth.delivery.CredentialDeliveryPort;
import kr.hs.gbsw.communication.auth.delivery.CredentialDeliveryReceipt;
import kr.hs.gbsw.communication.auth.delivery.CredentialDeliveryRepository;
import kr.hs.gbsw.communication.auth.delivery.CredentialDeliveryType;
import kr.hs.gbsw.communication.auth.domain.AccountRecord;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.auth.repository.AuthRepository;
import kr.hs.gbsw.communication.auth.service.RecentAuthenticationGuard;
import kr.hs.gbsw.communication.governance.domain.PrivilegedChangeRequestRecord;
import kr.hs.gbsw.communication.governance.domain.PrivilegedChangeStatus;
import kr.hs.gbsw.communication.governance.domain.PrivilegedChangeType;
import kr.hs.gbsw.communication.governance.dto.request.PrivilegedChangeApprovalRequest;
import kr.hs.gbsw.communication.governance.dto.request.PrivilegedChangeCreateRequest;
import kr.hs.gbsw.communication.governance.dto.response.PrivilegedChangeResponse;
import kr.hs.gbsw.communication.governance.repository.PrivilegedChangeRepository;
import kr.hs.gbsw.communication.governance.repository.PrivilegedChangeRepository.AuthenticatedRequester;
import kr.hs.gbsw.communication.user.domain.ProvisionedCode;
import kr.hs.gbsw.communication.user.domain.AccountRole;
import kr.hs.gbsw.communication.user.exception.AccountNotFoundException;
import kr.hs.gbsw.communication.user.exception.AccountStateConflictException;
import kr.hs.gbsw.communication.user.service.AccountLifecycleService;
import kr.hs.gbsw.communication.user.service.UserAdministrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivilegedChangeService {

    private final PrivilegedChangeRepository repository;
    private final AuthRepository authRepository;
    private final UserAdministrationService userService;
    private final AccountLifecycleService lifecycleService;
    private final CredentialDeliveryPort deliveryPort;
    private final CredentialDeliveryRepository deliveryRepository;
    private final AuditLogRepository auditLogRepository;
    private final RecentAuthenticationGuard recentAuthenticationGuard;
    private final Clock clock;
    private final Duration requestTtl;

    public PrivilegedChangeService(
            PrivilegedChangeRepository repository,
            AuthRepository authRepository,
            UserAdministrationService userService,
            AccountLifecycleService lifecycleService,
            CredentialDeliveryPort deliveryPort,
            CredentialDeliveryRepository deliveryRepository,
            AuditLogRepository auditLogRepository,
            RecentAuthenticationGuard recentAuthenticationGuard,
            Clock clock,
            @Value("${GOVERNANCE_REQUEST_TTL:PT30M}") Duration requestTtl
    ) {
        this.repository = repository;
        this.authRepository = authRepository;
        this.userService = userService;
        this.lifecycleService = lifecycleService;
        this.deliveryPort = deliveryPort;
        this.deliveryRepository = deliveryRepository;
        this.auditLogRepository = auditLogRepository;
        this.recentAuthenticationGuard = recentAuthenticationGuard;
        this.clock = clock;
        this.requestTtl = requestTtl;
    }

    @Transactional
    public PrivilegedChangeResponse request(
            AuthPrincipal actor,
            PrivilegedChangeCreateRequest request,
            String traceId
    ) {
        requireSuperAdmin(actor);
        recentAuthenticationGuard.requireRecent(actor);
        PrivilegedChangeRequestValidator.validate(request);
        Instant now = clock.instant();
        UUID targetUserId = request.targetUserPublicId() == null
                ? null
                : authRepository.lockByPublicId(request.targetUserPublicId())
                        .orElseThrow(AccountNotFoundException::new).id();
        UUID id = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        repository.insert(
                id, publicId, targetUserId, new AuthenticatedRequester(actor.userId()),
                request, now, now.plus(requestTtl));
        auditLogRepository.appendForTarget(
                actor.userId(), "GOVERNANCE_CHANGE_REQUESTED", "PRIVILEGED_CHANGE",
                publicId, "SUCCESS", traceId, now);
        return PrivilegedChangeResponse.from(
                repository.lockByPublicId(publicId).orElseThrow(), now);
    }

    @Transactional(readOnly = true)
    public List<PrivilegedChangeResponse> list(AuthPrincipal actor, int size) {
        requireSuperAdmin(actor);
        Instant now = clock.instant();
        return repository.findRecent(size).stream()
                .map(record -> PrivilegedChangeResponse.from(record, now))
                .toList();
    }

    @Transactional
    public PrivilegedChangeResponse approve(
            AuthPrincipal actor,
            UUID requestPublicId,
            PrivilegedChangeApprovalRequest approval,
            String traceId
    ) {
        requireSuperAdmin(actor);
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        PrivilegedChangeRequestRecord request = repository.lockByPublicId(requestPublicId)
                .orElseThrow(AccountNotFoundException::new);
        boolean sameAdministrator = request.requestedByUserId().equals(actor.userId());
        boolean bootstrapQuorumException = sameAdministrator
                && isBootstrapQuorumException(request, now);
        if (sameAdministrator && !bootstrapQuorumException) {
            throw new AccessDeniedException("The requester cannot approve the same privileged change");
        }
        if (request.status() != PrivilegedChangeStatus.PENDING) {
            throw new AccountStateConflictException("이미 실행된 권한 변경 요청입니다.");
        }
        if (!now.isBefore(request.expiresAt())) {
            throw new AccountStateConflictException("만료된 권한 변경 요청입니다.");
        }
        auditLogRepository.appendForTarget(
                actor.userId(), "GOVERNANCE_CHANGE_APPROVED", "PRIVILEGED_CHANGE",
                requestPublicId, "SUCCESS", traceId, now);
        GovernanceExecutionContext.execute(() -> {
            execute(request, actor, traceId, now);
            return null;
        });
        repository.markExecuted(
                request.id(), request.requestedByUserId(), actor.userId(),
                bootstrapQuorumException, approval.reason(), now);
        if (bootstrapQuorumException) {
            auditLogRepository.appendForTarget(
                    actor.userId(), "GOVERNANCE_BOOTSTRAP_QUORUM_APPROVED", "PRIVILEGED_CHANGE",
                    requestPublicId, "SUCCESS", traceId, now);
        }
        auditLogRepository.appendForTarget(
                actor.userId(), "GOVERNANCE_CHANGE_EXECUTED", "PRIVILEGED_CHANGE",
                requestPublicId, "SUCCESS", traceId, now);
        return PrivilegedChangeResponse.from(
                repository.lockByPublicId(requestPublicId).orElseThrow(), now);
    }

    private void execute(
            PrivilegedChangeRequestRecord request,
            AuthPrincipal approver,
            String traceId,
            Instant now
    ) {
        switch (request.type()) {
            case CREATE_ACCOUNT -> deliverCreatedAccount(
                    request,
                    userService.createAccount(
                            approver, request.loginId(), request.displayName(), request.role(),
                            request.reason(), traceId),
                    now);
            case REISSUE_ACTIVATION_CODE -> deliverExistingAccount(
                    request,
                    userService.reissueActivationCode(
                            approver, request.targetUserPublicId(), traceId),
                    CredentialDeliveryType.ACTIVATION,
                    now);
            case ISSUE_PASSWORD_RESET_CODE -> deliverExistingAccount(
                    request,
                    userService.issuePasswordResetCode(
                            approver, request.targetUserPublicId(), traceId),
                    CredentialDeliveryType.PASSWORD_RESET,
                    now);
            case ASSIGN_ROLE -> lifecycleService.assignRole(
                    approver, request.targetUserPublicId(), request.role(), request.startsAt(), request.endsAt(),
                    request.reason(), traceId);
            case END_ROLE -> lifecycleService.endRole(
                    approver, request.targetUserPublicId(), request.role(), request.endsAt(),
                    request.reason(), traceId);
            case APPOINT_OFFICE -> lifecycleService.appointOffice(
                    approver, request.targetUserPublicId(), request.office(), request.startsAt(), request.endsAt(),
                    request.replaceExistingAtStart(), request.reason(), traceId);
            case END_OFFICE -> lifecycleService.endOffice(
                    approver, request.targetUserPublicId(), request.office(), request.endsAt(),
                    request.reason(), traceId);
        }
    }

    private void deliverCreatedAccount(
            PrivilegedChangeRequestRecord request,
            ProvisionedCode code,
            Instant now
    ) {
        AccountRecord account = authRepository.lockByPublicId(code.userPublicId())
                .orElseThrow(AccountNotFoundException::new);
        repository.attachTargetUser(request.id(), account.id());
        deliver(request, account, request.loginId(), code, CredentialDeliveryType.ACTIVATION, now);
    }

    private void deliverExistingAccount(
            PrivilegedChangeRequestRecord request,
            ProvisionedCode code,
            CredentialDeliveryType type,
            Instant now
    ) {
        AccountRecord account = authRepository.lockByPublicId(code.userPublicId())
                .orElseThrow(AccountNotFoundException::new);
        deliver(request, account, account.loginId(), code, type, now);
    }

    private void deliver(
            PrivilegedChangeRequestRecord request,
            AccountRecord account,
            String recipientReference,
            ProvisionedCode code,
            CredentialDeliveryType type,
            Instant now
    ) {
        CredentialDeliveryReceipt receipt = deliveryPort.deliver(new CredentialDeliveryCommand(
                request.publicId(), code.userPublicId(), recipientReference, type,
                code.code(), code.expiresAt()));
        deliveryRepository.recordDelivered(request.id(), account.id(), type, receipt, now);
    }

    private void requireSuperAdmin(AuthPrincipal actor) {
        if (!actor.authorities().contains("ROLE_SUPER_ADMIN")) {
            throw new AccessDeniedException("Privileged governance requires SUPER_ADMIN");
        }
    }

    private boolean isBootstrapQuorumException(PrivilegedChangeRequestRecord request, Instant now) {
        int provisionedSuperAdministrators = repository.lockProvisionedSuperAdministratorCount(now);
        if (request.type() == PrivilegedChangeType.CREATE_ACCOUNT) {
            return request.role() == AccountRole.SUPER_ADMIN && provisionedSuperAdministrators == 1;
        }
        return request.type() == PrivilegedChangeType.REISSUE_ACTIVATION_CODE
                && provisionedSuperAdministrators == 2
                && request.targetUserId() != null
                && repository.isPendingSuperAdministrator(request.targetUserId(), now);
    }
}
