package kr.hs.gbsw.communication.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.user.domain.ProvisionedCode;

@Schema(description = "한 번만 표시되는 계정 코드. 응답을 로그·브라우저 저장소에 보관하지 않습니다.")
public record OneTimeCodeResponse(
        UUID userPublicId,
        @Schema(example = "7Kp9zT4Wh3NmQ6Rx") String code,
        Instant expiresAt
) {
    public static OneTimeCodeResponse from(ProvisionedCode provisionedCode) {
        return new OneTimeCodeResponse(
                provisionedCode.userPublicId(),
                provisionedCode.code(),
                provisionedCode.expiresAt());
    }
}
