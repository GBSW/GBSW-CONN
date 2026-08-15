package kr.hs.gbsw.communication.common.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import kr.hs.gbsw.communication.common.config.DeploymentSecurityProperties;
import kr.hs.gbsw.communication.proposal.config.IdentityVaultProperties;
import kr.hs.gbsw.communication.proposal.config.ProposalOwnershipProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class ProductionSecurityStartupValidator implements SmartInitializingSingleton {

    private static final Set<String> PLACEHOLDER_SECRETS = Set.of(
            "change-me", "changeme", "replace-me", "example", "test", "secret", "password");

    private final Environment environment;
    private final ApplicationSecurityProperties securityProperties;
    private final DeploymentSecurityProperties deploymentProperties;
    private final IdentityVaultProperties identityVaultProperties;
    private final ProposalOwnershipProperties ownershipProperties;

    public ProductionSecurityStartupValidator(
            Environment environment,
            ApplicationSecurityProperties securityProperties,
            DeploymentSecurityProperties deploymentProperties,
            IdentityVaultProperties identityVaultProperties,
            ProposalOwnershipProperties ownershipProperties
    ) {
        this.environment = environment;
        this.securityProperties = securityProperties;
        this.deploymentProperties = deploymentProperties;
        this.identityVaultProperties = identityVaultProperties;
        this.ownershipProperties = ownershipProperties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean prodProfile = environment.acceptsProfiles(Profiles.of("prod"));
        if (!prodProfile && !deploymentProperties.production()) {
            return;
        }

        List<String> violations = new ArrayList<>();
        require(prodProfile, "spring.profiles.active must include prod", violations);
        require(deploymentProperties.production(), "app.deployment.production must be true", violations);
        require(securityProperties.cookie().secure(), "app.security.cookie.secure must be true", violations);
        require(booleanProperty("server.servlet.session.cookie.secure", false),
                "server.servlet.session.cookie.secure must be true", violations);
        require(booleanProperty("server.servlet.session.cookie.http-only", true),
                "server.servlet.session.cookie.http-only must be true", violations);
        require(!booleanProperty("springdoc.api-docs.enabled", true),
                "springdoc.api-docs.enabled must be false", violations);
        require(!booleanProperty("springdoc.swagger-ui.enabled", true),
                "springdoc.swagger-ui.enabled must be false", violations);
        require("none".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy", "native")),
                "server.forward-headers-strategy must be none", violations);
        require(!deploymentProperties.trustedProxyCidrs().isEmpty(),
                "app.deployment.trusted-proxy-cidrs must not be empty", violations);
        require(deploymentProperties.authenticationRequestsPerMinute()
                        <= securityProperties.rateLimit().generalRequestsPerMinute(),
                "authentication request limit must not exceed the general request limit", violations);
        requireSecret("spring.datasource.password", violations);
        requireSecret("app.security.secrets.throttle-fingerprint", violations);
        require(identityVaultProperties.activeKey().length == 32,
                "an active 32-byte Identity Vault key is required", violations);
        require(ownershipProperties.activeKey().length == 32,
                "an active 32-byte proposal ownership key is required", violations);

        if (!violations.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", violations));
        }
    }

    private boolean booleanProperty(String name, boolean defaultValue) {
        return environment.getProperty(name, Boolean.class, defaultValue);
    }

    private void requireSecret(String name, List<String> violations) {
        String value = environment.getProperty(name);
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        require(!normalized.isEmpty()
                        && !normalized.contains("${")
                        && !PLACEHOLDER_SECRETS.contains(normalized),
                name + " must be a non-placeholder secret", violations);
    }

    private void require(boolean condition, String message, List<String> violations) {
        if (!condition) {
            violations.add(message);
        }
    }
}
