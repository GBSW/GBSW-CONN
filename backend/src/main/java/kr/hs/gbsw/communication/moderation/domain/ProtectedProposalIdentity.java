package kr.hs.gbsw.communication.moderation.domain;

import java.util.UUID;
import kr.hs.gbsw.communication.proposal.domain.EncryptedProposalIdentity;

public record ProtectedProposalIdentity(
        UUID proposalPublicId,
        EncryptedProposalIdentity identity
) {
}
