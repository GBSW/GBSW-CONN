package kr.hs.gbsw.communication.proposal.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.hs.gbsw.communication.proposal.domain.AuthorVisibility;

@Schema(description = "학생 공개 제안 작성")
public record CreateProposalRequest(
        @NotBlank
        @Size(max = 200)
        @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]+", message = "제목에 제어 문자를 사용할 수 없습니다.")
        String title,

        @NotBlank
        @Size(max = 10000)
        @Pattern(
                regexp = "(?s)(?:[^\\p{Cc}\\p{Cf}]|[\\r\\n\\t])+",
                message = "본문에 허용되지 않는 제어 문자가 있습니다.")
        String content,

        @NotNull AuthorVisibility authorVisibility
) {
}
