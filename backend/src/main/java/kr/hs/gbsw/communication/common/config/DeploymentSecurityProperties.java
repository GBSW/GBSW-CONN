package kr.hs.gbsw.communication.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.deployment")
public record DeploymentSecurityProperties(
        @DefaultValue("false") boolean production,
        @DefaultValue("X-Gbsw-Client-IP") @NotBlank
        @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String trustedClientAddressHeader,
        List<String> trustedProxyCidrs,
        @DefaultValue("50000") @Min(100) @Max(500000) int requestLimiterMaxEntries,
        @DefaultValue("PT15M") @NotNull Duration requestLimiterIdleTtl,
        @DefaultValue("30") @Min(1) @Max(1000) int authenticationRequestsPerMinute,
        @DefaultValue("4") @Min(1) @Max(64) int credentialVerificationConcurrency,
        @DefaultValue("50000") @Min(100) @Max(500000) int unknownAccountMaxEntries,
        @DefaultValue("PT15M") @NotNull Duration unknownAccountTtl,
        @DefaultValue("PT24H") @NotNull Duration persistentThrottleTtl,
        @DefaultValue("PT720H") @NotNull Duration authAuditRetention,
        @DefaultValue("1000") @Min(1) @Max(10000) int maintenanceBatchSize,
        @DefaultValue("PT1H") @NotNull Duration maintenanceInterval
) {
    public DeploymentSecurityProperties {
        trustedProxyCidrs = trustedProxyCidrs == null ? List.of() : List.copyOf(trustedProxyCidrs);
        requirePositive(requestLimiterIdleTtl, "requestLimiterIdleTtl");
        requirePositive(unknownAccountTtl, "unknownAccountTtl");
        requirePositive(persistentThrottleTtl, "persistentThrottleTtl");
        requirePositive(authAuditRetention, "authAuditRetention");
        requirePositive(maintenanceInterval, "maintenanceInterval");
        if ("X-Forwarded-For".equalsIgnoreCase(trustedClientAddressHeader)) {
            throw new IllegalArgumentException("X-Forwarded-For cannot be used as the trusted client address header");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
