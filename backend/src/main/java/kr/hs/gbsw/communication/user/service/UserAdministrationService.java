package kr.hs.gbsw.communication.user.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AccountRecord;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.auth.repository.AuthRepository;
import kr.hs.gbsw.communication.auth.service.OneTimeCodeGenerator;
import kr.hs.gbsw.communication.auth.service.RecentAuthenticationGuard;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import kr.hs.gbsw.communication.user.domain.AccountRole;
import kr.hs.gbsw.communication.user.domain.AccountStatus;
import kr.hs.gbsw.communication.user.domain.ProvisionedCode;
import kr.hs.gbsw.communication.user.exception.AccountConflictException;
import kr.hs.gbsw.communication.user.exception.AccountNotFoundException;
import kr.hs.gbsw.communication.user.exception.AccountStateConflictException;
import kr.hs.gbsw.communication.user.repository.UserAdministrationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {

    private final UserAdministrationRepository repository;
    private final AuthRepository authRepository;
    private final AuditLogRepository auditLogRepository;
    private final OneTimeCodeGenerator codeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationSecurityProperties properties;
    private final Clock clock;
    private final RecentAuthenticationGuard recentAuthenticationGuard;

    public UserAdministrationService(
            UserAdministrationRepository repository,
            AuthRepository authRepository,
            AuditLogRepository auditLogRepository,
            OneTimeCodeGenerator codeGenerator,
            PasswordEncoder passwordEncoder,
            ApplicationSecurityProperties properties,
            Clock clock,
            RecentAuthenticationGuard recentAuthenticationGuard
    ) {
        this.repository = repository;
        this.authRepository = authRepository;
        this.auditLogRepository = auditLogRepository;
        this.codeGenerator = codeGenerator;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.recentAuthenticationGuard = recentAuthenticationGuard;
    }

    @Transactional
    public ProvisionedCode createAccount(
            AuthPrincipal actor,
            String loginId,
            String displayName,
            AccountRole role,
            String reason,
            String traceId
    ) {
        kr.hs.gbsw.communication.governance.service.GovernanceExecutionContext.requireActive();
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        UUID userId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        String code = codeGenerator.generate();
        Instant expiresAt = now.plus(properties.credentials().activationCodeTtl());
        try {
            repository.insertPendingAccount(userId, publicId, loginId, displayName, now);
        } catch (DataIntegrityViolationException exception) {
            throw new AccountConflictException();
        }
        repository.insertRoleAssignment(userId, role, actor.userId(), now, reason);
        repository.insertActivationCode(
                userId,
                passwordEncoder.encode(code),
                expiresAt,
                actor.userId(),
                now);
        auditLogRepository.append(actor.userId(), "ADMIN_ACCOUNT_CREATED", publicId, "SUCCESS", traceId, now);
        return new ProvisionedCode(publicId, code, expiresAt);
    }

    @Transactional
    public ProvisionedCode reissueActivationCode(AuthPrincipal actor, UUID publicId, String traceId) {
        kr.hs.gbsw.communication.governance.service.GovernanceExecutionContext.requireActive();
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        AccountRecord account = authRepository.lockByPublicId(publicId)
                .orElseThrow(AccountNotFoundException::new);
        if (account.status() != AccountStatus.PENDING_ACTIVATION || account.hasCredentials()) {
            throw new AccountStateConflictException("활성화 대기 계정에만 가입 코드를 발급할 수 있습니다.");
        }
        String code = codeGenerator.generate();
        Instant expiresAt = now.plus(properties.credentials().activationCodeTtl());
        repository.revokeActivationCodes(account.id(), now);
        repository.insertActivationCode(
                account.id(), passwordEncoder.encode(code), expiresAt, actor.userId(), now);
        auditLogRepository.append(actor.userId(), "ADMIN_ACTIVATION_CODE_ISSUED", publicId, "SUCCESS", traceId, now);
        return new ProvisionedCode(publicId, code, expiresAt);
    }

    @Transactional
    public ProvisionedCode issuePasswordResetCode(AuthPrincipal actor, UUID publicId, String traceId) {
        kr.hs.gbsw.communication.governance.service.GovernanceExecutionContext.requireActive();
        recentAuthenticationGuard.requireRecent(actor);
        Instant now = clock.instant();
        AccountRecord account = authRepository.lockByPublicId(publicId)
                .orElseThrow(AccountNotFoundException::new);
        if (account.status() != AccountStatus.ACTIVE || !account.hasCredentials()) {
            throw new AccountStateConflictException("활성 계정에만 비밀번호 재설정 코드를 발급할 수 있습니다.");
        }
        String code = codeGenerator.generate();
        Instant expiresAt = now.plus(properties.credentials().passwordResetCodeTtl());
        repository.revokePasswordResetCodes(account.id(), now);
        repository.insertPasswordResetCode(
                account.id(), passwordEncoder.encode(code), expiresAt, actor.userId(), now);
        auditLogRepository.append(actor.userId(), "ADMIN_PASSWORD_RESET_CODE_ISSUED", publicId, "SUCCESS", traceId, now);
        return new ProvisionedCode(publicId, code, expiresAt);
    }
}
