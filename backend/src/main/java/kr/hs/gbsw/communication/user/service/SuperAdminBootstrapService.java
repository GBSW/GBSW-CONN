package kr.hs.gbsw.communication.user.service;

import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.user.domain.AccountRole;
import kr.hs.gbsw.communication.user.exception.BootstrapAlreadyCompletedException;
import kr.hs.gbsw.communication.user.repository.UserAdministrationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuperAdminBootstrapService {

    private final UserAdministrationRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminBootstrapService(
            UserAdministrationRepository repository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UUID bootstrap(
            String loginId,
            String displayName,
            String activationCode,
            Instant expiresAt,
            Instant now,
            String traceId
    ) {
        if (!repository.acquireSuperAdminBootstrapMarker(now)) {
            throw new BootstrapAlreadyCompletedException();
        }

        UUID userId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        repository.insertPendingAccount(userId, publicId, loginId, displayName, now);
        repository.insertRoleAssignment(
                userId,
                AccountRole.SUPER_ADMIN,
                userId,
                now,
                "최초 슈퍼 어드민 부트스트랩");
        repository.insertActivationCode(
                userId,
                passwordEncoder.encode(activationCode),
                expiresAt,
                userId,
                now);
        repository.completeSuperAdminBootstrapMarker(userId, now);
        auditLogRepository.append(
                userId,
                "BOOTSTRAP_SUPER_ADMIN_CREATED",
                publicId,
                "SUCCESS",
                traceId,
                now);
        return publicId;
    }
}
