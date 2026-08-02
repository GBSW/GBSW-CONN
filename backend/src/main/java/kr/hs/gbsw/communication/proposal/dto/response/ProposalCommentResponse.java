package kr.hs.gbsw.communication.proposal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.domain.ProposalCommentRecord;

@Schema(description = "제안 댓글")
public record ProposalCommentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID publicId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String authorDisplayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean viewerCanDelete,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt
) {
    public static ProposalCommentResponse from(ProposalCommentRecord comment) {
        return new ProposalCommentResponse(
                comment.publicId(), comment.authorDisplayName(), comment.content(),
                comment.viewerCanDelete(), comment.createdAt());
    }
}
