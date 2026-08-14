package kr.hs.gbsw.communication.common.maintenance;

import java.time.Clock;
import java.time.Instant;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.repository.ThrottleRepository;
import kr.hs.gbsw.communication.common.config.DeploymentSecurityProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityDataRetentionService {

    private final ThrottleRepository throttleRepository;
    private final AuditLogRepository auditLogRepository;
    private final DeploymentSecurityProperties properties;
    private final Clock clock;

    public SecurityDataRetentionService(
            ThrottleRepository throttleRepository,
            AuditLogRepository auditLogRepository,
            DeploymentSecurityProperties properties,
            Clock clock
    ) {
        this.throttleRepository = throttleRepository;
        this.auditLogRepository = auditLogRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.deployment.maintenance-interval:PT1H}")
    @Transactional
    public void deleteExpiredSecurityData() {
        Instant now = clock.instant();
        throttleRepository.deleteExpired(now, properties.maintenanceBatchSize());
        auditLogRepository.deleteExpiredAuthenticationEvents(
                now.minus(properties.authAuditRetention()),
                properties.maintenanceBatchSize());
    }
}
