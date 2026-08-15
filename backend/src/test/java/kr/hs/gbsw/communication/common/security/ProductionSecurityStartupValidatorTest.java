package kr.hs.gbsw.communication.common.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import kr.hs.gbsw.communication.common.config.DeploymentSecurityProperties;
import kr.hs.gbsw.communication.proposal.config.IdentityVaultProperties;
import kr.hs.gbsw.communication.proposal.config.ProposalOwnershipProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecurityStartupValidatorTest {

    @Test
    void productionRefusesContainerForwardingEvenWhenOtherControlsAreSafe() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.servlet.session.cookie.secure", "true")
                .withProperty("server.servlet.session.cookie.http-only", "true")
                .withProperty("springdoc.api-docs.enabled", "false")
                .withProperty("springdoc.swagger-ui.enabled", "false")
                .withProperty("server.forward-headers-strategy", "native")
                .withProperty("spring.datasource.password", "db-production-value")
                .withProperty("app.security.secrets.throttle-fingerprint", "throttle-production-value")
                .withProperty("app.identity-vault.key-base64", "identity-production-value");
        environment.setActiveProfiles("prod");

        ProductionSecurityStartupValidator validator = new ProductionSecurityStartupValidator(
                environment, securityProperties(), deploymentProperties(),
                identityVaultProperties(), proposalOwnershipProperties());

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server.forward-headers-strategy must be none");
    }

    private ApplicationSecurityProperties securityProperties() {
        return new ApplicationSecurityProperties(
                new ApplicationSecurityProperties.Cookie(true, "Lax"),
                new ApplicationSecurityProperties.RateLimit(5, 300, 120),
                new ApplicationSecurityProperties.Credentials(
                        Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofMinutes(10), 12, 128),
                new ApplicationSecurityProperties.Secrets("01234567890123456789012345678901"));
    }

    private DeploymentSecurityProperties deploymentProperties() {
        return new DeploymentSecurityProperties(
                true, "X-Gbsw-Client-IP", List.of("10.0.0.0/8"),
                100, Duration.ofMinutes(15), 30, 2,
                100, Duration.ofMinutes(15), Duration.ofHours(24),
                Duration.ofDays(30), 100, Duration.ofHours(1));
    }

    private IdentityVaultProperties identityVaultProperties() {
        IdentityVaultProperties properties = new IdentityVaultProperties();
        properties.setKeyBase64(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setKeyVersion(1);
        properties.afterPropertiesSet();
        return properties;
    }

    private ProposalOwnershipProperties proposalOwnershipProperties() {
        ProposalOwnershipProperties properties = new ProposalOwnershipProperties();
        properties.setKeyBase64(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setKeyVersion(1);
        properties.afterPropertiesSet();
        return properties;
    }
}
