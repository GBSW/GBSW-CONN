package kr.hs.gbsw.communication.auth.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AccountRecord;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.auth.domain.StoredOneTimeCode;
import kr.hs.gbsw.communication.auth.domain.ThrottleOperation;
import kr.hs.gbsw.communication.auth.exception.ActivationFailedException;
import kr.hs.gbsw.communication.auth.exception.AuthenticationFailedException;
import kr.hs.gbsw.communication.auth.exception.AuthenticationThrottledException;
import kr.hs.gbsw.communication.auth.exception.PasswordResetFailedException;
import kr.hs.gbsw.communication.auth.repository.AuthRepository;
import kr.hs.gbsw.communication.user.domain.AccountStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthRepository authRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuthenticationThrottleService throttleService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            AuthRepository authRepository,
            AuditLogRepository auditLogRepository,
            AuthenticationThrottleService throttleService,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.authRepository = authRepository;
        this.auditLogRepository = auditLogRepository;
        this.throttleService = throttleService;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        byte[] dummySecret = new byte[32];
        new SecureRandom().nextBytes(dummySecret);
        this.dummyPasswordHash = passwordEncoder.encode(Base64.getEncoder().encodeToString(dummySecret));
    }

    @Transactional(noRollbackFor = {
            AuthenticationFailedException.class,
            AuthenticationThrottledException.class
    })
    public AuthPrincipal login(String loginId, String password, String remoteAddress, String traceId) {
        Instant now = clock.instant();
        Optional<AccountRecord> candidate = authRepository.findByLoginId(loginId);
        try {
            throttleService.assertAllowed(ThrottleOperation.LOGIN, loginId, remoteAddress, now);
        } catch (AuthenticationThrottledException exception) {
            appendAudit(candidate.orElse(null), "AUTH_LOGIN", "BLOCKED", traceId, now);
            throw exception;
        }

        AccountRecord account = candidate.orElse(null);
        String storedHash = account != null && account.hasCredentials()
                ? account.passwordHash()
                : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);
        boolean usable = account != null
                && account.status() == AccountStatus.ACTIVE
                && account.hasCredentials()
                && passwordMatches;

        if (!usable) {
            throttleService.recordFailure(ThrottleOperation.LOGIN, loginId, remoteAddress, now);
            appendAudit(account, "AUTH_LOGIN", "FAILURE", traceId, now);
            throw new AuthenticationFailedException();
        }

        throttleService.clear(ThrottleOperation.LOGIN, loginId, remoteAddress);
        appendAudit(account, "AUTH_LOGIN", "SUCCESS", traceId, now);
        return authRepository.toPrincipal(account, now, now);
    }

    @Transactional(noRollbackFor = {
            AuthenticationFailedException.class,
            AuthenticationThrottledException.class
    })
    public AuthPrincipal reauthenticate(
            AuthPrincipal currentPrincipal,
            String password,
            String remoteAddress,
            String traceId
    ) {
        Instant now = clock.instant();
        AccountRecord account = authRepository.findByLoginId(currentPrincipal.loginId()).orElse(null);
        try {
            throttleService.assertAllowed(
                    ThrottleOperation.REAUTHENTICATION,
                    currentPrincipal.loginId(),
                    remoteAddress,
                    now);
        } catch (AuthenticationThrottledException exception) {
            appendAudit(account, "AUTH_REAUTHENTICATION", "BLOCKED", traceId, now);
            throw exception;
        }

        String storedHash = account != null && account.hasCredentials()
                ? account.passwordHash()
                : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);
        boolean usable = account != null
                && account.id().equals(currentPrincipal.userId())
                && account.status() == AccountStatus.ACTIVE
                && account.hasCredentials()
                && account.credentialVersion() == currentPrincipal.credentialVersion()
                && passwordMatches;
        if (!usable) {
            throttleService.recordFailure(
                    ThrottleOperation.REAUTHENTICATION,
                    currentPrincipal.loginId(),
                    remoteAddress,
                    now);
            appendAudit(account, "AUTH_REAUTHENTICATION", "FAILURE", traceId, now);
            throw new AuthenticationFailedException();
        }

        throttleService.clear(
                ThrottleOperation.REAUTHENTICATION,
                currentPrincipal.loginId(),
                remoteAddress);
        appendAudit(account, "AUTH_REAUTHENTICATION", "SUCCESS", traceId, now);
        return authRepository.toPrincipal(account, now, now);
    }

    @Transactional(noRollbackFor = {
            ActivationFailedException.class,
            AuthenticationThrottledException.class
    })
    public void activate(
            String loginId,
            String activationCode,
            String password,
            String remoteAddress,
            String traceId
    ) {
        passwordPolicy.validate(password);
        Instant now = clock.instant();
        AccountRecord account = authRepository.lockByLoginId(loginId).orElse(null);
        try {
            throttleService.assertAllowed(ThrottleOperation.ACTIVATION, loginId, remoteAddress, now);
        } catch (AuthenticationThrottledException exception) {
            appendAudit(account, "AUTH_ACTIVATION", "BLOCKED", traceId, now);
            throw exception;
        }

        List<StoredOneTimeCode> codes = account == null
                ? List.of()
                : authRepository.lockUsableActivationCodes(account.id(), now);
        StoredOneTimeCode matchedCode = findMatchingCode(activationCode, codes).orElse(null);
        boolean usable = account != null
                && account.status() == AccountStatus.PENDING_ACTIVATION
                && !account.hasCredentials()
                && matchedCode != null;

        if (!usable) {
            throttleService.recordFailure(ThrottleOperation.ACTIVATION, loginId, remoteAddress, now);
            appendAudit(account, "AUTH_ACTIVATION", "FAILURE", traceId, now);
            throw new ActivationFailedException();
        }

        authRepository.markActivationCodeUsed(matchedCode.id(), now);
        authRepository.revokeOtherActivationCodes(account.id(), matchedCode.id(), now);
        authRepository.activateAccount(account.id(), passwordEncoder.encode(password), now);
        throttleService.clear(ThrottleOperation.ACTIVATION, loginId, remoteAddress);
        appendAudit(account, "AUTH_ACTIVATION", "SUCCESS", traceId, now);
    }

    @Transactional(noRollbackFor = {
            PasswordResetFailedException.class,
            AuthenticationThrottledException.class
    })
    public void resetPassword(
            String loginId,
            String resetCode,
            String newPassword,
            String remoteAddress,
            String traceId
    ) {
        passwordPolicy.validate(newPassword);
        Instant now = clock.instant();
        AccountRecord account = authRepository.lockByLoginId(loginId).orElse(null);
        try {
            throttleService.assertAllowed(ThrottleOperation.PASSWORD_RESET, loginId, remoteAddress, now);
        } catch (AuthenticationThrottledException exception) {
            appendAudit(account, "AUTH_PASSWORD_RESET", "BLOCKED", traceId, now);
            throw exception;
        }

        List<StoredOneTimeCode> codes = account == null
                ? List.of()
                : authRepository.lockUsablePasswordResetCodes(account.id(), now);
        StoredOneTimeCode matchedCode = findMatchingCode(resetCode, codes).orElse(null);
        boolean usable = account != null
                && account.status() == AccountStatus.ACTIVE
                && account.hasCredentials()
                && matchedCode != null;

        if (!usable) {
            throttleService.recordFailure(ThrottleOperation.PASSWORD_RESET, loginId, remoteAddress, now);
            appendAudit(account, "AUTH_PASSWORD_RESET", "FAILURE", traceId, now);
            throw new PasswordResetFailedException();
        }

        authRepository.markPasswordResetCodeUsed(matchedCode.id(), now);
        authRepository.revokeOtherPasswordResetCodes(account.id(), matchedCode.id(), now);
        authRepository.resetPassword(account.id(), passwordEncoder.encode(newPassword), now);
        authRepository.deleteSessions(account.loginId());
        throttleService.clear(ThrottleOperation.PASSWORD_RESET, loginId, remoteAddress);
        appendAudit(account, "AUTH_PASSWORD_RESET", "SUCCESS", traceId, now);
    }

    private Optional<StoredOneTimeCode> findMatchingCode(String rawCode, List<StoredOneTimeCode> codes) {
        if (codes.isEmpty()) {
            passwordEncoder.matches(rawCode, dummyPasswordHash);
            return Optional.empty();
        }
        StoredOneTimeCode code = codes.getFirst();
        return passwordEncoder.matches(rawCode, code.codeHash())
                ? Optional.of(code)
                : Optional.empty();
    }

    private void appendAudit(AccountRecord account, String eventType, String outcome, String traceId, Instant now) {
        auditLogRepository.append(
                account == null ? null : account.id(),
                eventType,
                account == null ? null : account.publicId(),
                outcome,
                traceId,
                now);
    }
}
