package kr.hs.gbsw.communication.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.web.csrf.CsrfToken;

@Schema(description = "상태 변경 요청에 사용할 CSRF 토큰")
public record CsrfTokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String headerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String parameterName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token
) {
    public static CsrfTokenResponse from(CsrfToken token) {
        return new CsrfTokenResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }
}
