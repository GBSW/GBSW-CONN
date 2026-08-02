package kr.hs.gbsw.communication.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security")
public record ApplicationSecurityProperties(
        @Valid @NotNull Cookie cookie,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Credentials credentials,
        @Valid @NotNull Secrets secrets
) {
    public record Cookie(
            boolean secure,
            @NotBlank String sameSite
    ) {
    }

    public record RateLimit(
            @Min(1) @Max(100) int loginFailuresBeforeDelay,
            @Min(1) @Max(3600) int maximumDelaySeconds,
            @Min(1) @Max(1000) int generalRequestsPerMinute
    ) {
    }

    public record Credentials(
            @NotNull Duration activationCodeTtl,
            @NotNull Duration passwordResetCodeTtl,
            @NotNull Duration reauthenticationTtl,
            @Min(12) @Max(128) int minimumPasswordLength,
            @Min(12) @Max(256) int maximumPasswordLength
    ) {
        public Credentials {
            if (activationCodeTtl.isNegative() || activationCodeTtl.isZero()) {
                throw new IllegalArgumentException("activationCodeTtl must be positive");
            }
            if (passwordResetCodeTtl.isNegative() || passwordResetCodeTtl.isZero()) {
                throw new IllegalArgumentException("passwordResetCodeTtl must be positive");
            }
            if (reauthenticationTtl.isNegative() || reauthenticationTtl.isZero()) {
                throw new IllegalArgumentException("reauthenticationTtl must be positive");
            }
            if (maximumPasswordLength < minimumPasswordLength) {
                throw new IllegalArgumentException("maximumPasswordLength must be at least minimumPasswordLength");
            }
        }
    }

    public static final class Secrets {
        private final String throttleFingerprint;

        public Secrets(@NotBlank @Size(min = 32) String throttleFingerprint) {
            this.throttleFingerprint = throttleFingerprint;
        }

        public String throttleFingerprint() {
            return throttleFingerprint;
        }

        @Override
        public String toString() {
            return "Secrets[throttleFingerprint=REDACTED]";
        }
    }
}
