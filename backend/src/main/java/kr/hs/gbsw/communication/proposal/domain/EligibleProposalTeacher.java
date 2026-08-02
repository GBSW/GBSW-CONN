package kr.hs.gbsw.communication.proposal.domain;

import java.util.UUID;

public record EligibleProposalTeacher(
        UUID id,
        UUID publicId,
        String displayName
) {
}
