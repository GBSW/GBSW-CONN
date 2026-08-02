package kr.hs.gbsw.communication.proposal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProposalTransitionReasonRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
