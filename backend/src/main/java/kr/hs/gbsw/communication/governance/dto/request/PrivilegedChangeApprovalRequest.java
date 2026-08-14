package kr.hs.gbsw.communication.governance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrivilegedChangeApprovalRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
