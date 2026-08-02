package kr.hs.gbsw.communication.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;

@Schema(description = "현재 로그인 사용자에게 공개 가능한 계정 정보")
public record CurrentUserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID publicId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String loginId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> offices,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant reauthenticatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant reauthenticationExpiresAt
) {
    public static CurrentUserResponse from(AuthPrincipal principal, Duration reauthenticationTtl) {
        return new CurrentUserResponse(
                principal.publicId(),
                principal.loginId(),
                principal.displayName(),
                principal.authorities().stream()
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring("ROLE_".length()))
                        .toList(),
                principal.authorities().stream()
                        .filter(authority -> authority.startsWith("OFFICE_"))
                        .map(authority -> authority.substring("OFFICE_".length()))
                        .toList(),
                principal.reauthenticatedAt(),
                principal.reauthenticatedAt().plus(reauthenticationTtl));
    }
}
