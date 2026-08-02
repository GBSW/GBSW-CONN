package kr.hs.gbsw.communication.proposal.domain;

import java.time.Instant;
import java.util.UUID;

public record ProposalViewRecord(
        UUID publicId,
        String title,
        String content,
        AuthorVisibility authorVisibility,
        String authorDisplayName,
        ProposalWorkflowStatus workflowStatus,
        ProposalVisibilityStatus visibilityStatus,
        int supportCount,
        boolean viewerSupported,
        Instant formalizedAt,
        Integer formalizedSupportCount,
        Instant createdAt
) {
}
