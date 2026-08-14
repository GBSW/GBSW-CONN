package kr.hs.gbsw.communication.proposal.service;

import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.domain.EncryptedProposalIdentity;
import kr.hs.gbsw.communication.proposal.repository.ProposalIdentityRepository;
import org.springframework.stereotype.Service;

@Service
public class ProposalIdentityVaultService {

    private final ProposalIdentityCipher cipher;
    private final ProposalIdentityRepository repository;

    public ProposalIdentityVaultService(
            ProposalIdentityCipher cipher,
            ProposalIdentityRepository repository
    ) {
        this.cipher = cipher;
        this.repository = repository;
    }

    public void storeIdentity(UUID proposalId, UUID proposalPublicId, UUID userId, Instant now) {
        EncryptedProposalIdentity identity = cipher.encrypt(proposalPublicId, userId);
        repository.insertIdentity(proposalId, identity, now);
    }
}
