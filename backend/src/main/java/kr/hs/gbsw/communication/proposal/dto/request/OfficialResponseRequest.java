package kr.hs.gbsw.communication.proposal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OfficialResponseRequest(
        @NotBlank @Size(max = 10000) String content,
        @NotBlank @Size(max = 500) String decisionReason,
        @Size(max = 10000) String followUpPlan
) {
}
