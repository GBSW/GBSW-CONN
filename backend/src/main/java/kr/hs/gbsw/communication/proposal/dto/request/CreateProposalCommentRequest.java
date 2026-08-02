package kr.hs.gbsw.communication.proposal.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "학생 제안 댓글 작성")
public record CreateProposalCommentRequest(
        @NotBlank
        @Size(max = 2000)
        @Pattern(
                regexp = "(?s)(?:[^\\p{Cc}\\p{Cf}]|[\\r\\n\\t])+",
                message = "댓글에 허용되지 않는 제어 문자가 있습니다.")
        String content
) {
}
