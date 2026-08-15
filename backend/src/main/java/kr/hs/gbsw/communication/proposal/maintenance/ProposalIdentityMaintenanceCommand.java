package kr.hs.gbsw.communication.proposal.maintenance;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.repository.ProposalIdentityRepository;
import kr.hs.gbsw.communication.proposal.repository.ProposalIdentityRepository.MaintenanceCandidate;
import kr.hs.gbsw.communication.proposal.service.ProposalIdentityCipher;
import kr.hs.gbsw.communication.proposal.service.ProposalOwnershipTagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("maintenance")
@ConditionalOnProperty(name = "app.proposal-identity-maintenance.enabled", havingValue = "true")
public class ProposalIdentityMaintenanceCommand implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProposalIdentityMaintenanceCommand.class);

    private final ProposalIdentityRepository identityRepository;
    private final ProposalIdentityCipher identityCipher;
    private final ProposalOwnershipTagService ownershipTagService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int batchSize;

    public ProposalIdentityMaintenanceCommand(
            ProposalIdentityRepository identityRepository,
            ProposalIdentityCipher identityCipher,
            ProposalOwnershipTagService ownershipTagService,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${app.proposal-identity-maintenance.batch-size:100}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Proposal identity maintenance batch size must be between 1 and 1000");
        }
        this.identityRepository = identityRepository;
        this.identityCipher = identityCipher;
        this.ownershipTagService = ownershipTagService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        long tagsBackfilled = 0;
        long automaticSupportsRemoved = 0;
        while (true) {
            List<MaintenanceCandidate> candidates = identityRepository.findWithoutOwnershipTags(batchSize);
            if (candidates.isEmpty()) {
                break;
            }
            for (MaintenanceCandidate candidate : candidates) {
                UUID authorUserId = identityCipher.decrypt(
                        candidate.proposalPublicId(), candidate.identity());
                Instant now = clock.instant();
                MaintenanceResult result = transactionTemplate.execute(status -> {
                    boolean tagCreated = ownershipTagService.storeIfAbsent(
                            candidate.proposalId(), candidate.proposalPublicId(), authorUserId, now);
                    boolean supportRemoved = tagCreated && identityRepository.deleteAutomaticAuthorSupport(
                            candidate.proposalId(), authorUserId, candidate.proposalCreatedAt());
                    return new MaintenanceResult(tagCreated, supportRemoved);
                });
                if (result == null) {
                    throw new IllegalStateException("Proposal identity maintenance transaction returned no result");
                }
                if (result.tagCreated()) {
                    tagsBackfilled++;
                }
                if (result.supportRemoved()) {
                    automaticSupportsRemoved++;
                }
            }
        }
        log.info(
                "Proposal identity maintenance completed; tagsBackfilled={}, automaticSupportsRemoved={}",
                tagsBackfilled,
                automaticSupportsRemoved);
    }

    private record MaintenanceResult(boolean tagCreated, boolean supportRemoved) {
    }
}
