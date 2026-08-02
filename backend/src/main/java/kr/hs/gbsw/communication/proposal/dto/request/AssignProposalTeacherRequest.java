package kr.hs.gbsw.communication.proposal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AssignProposalTeacherRequest(
        @NotNull UUID teacherPublicId,
        @NotBlank @Size(max = 500) String reason
) {
}
