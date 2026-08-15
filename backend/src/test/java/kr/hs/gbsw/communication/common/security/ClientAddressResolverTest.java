package kr.hs.gbsw.communication.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import kr.hs.gbsw.communication.common.config.DeploymentSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {

    private final ClientAddressResolver resolver = new ClientAddressResolver(properties());

    @Test
    void acceptsDedicatedHeaderOnlyFromTrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Gbsw-Client-IP", "203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void ignoresXForwardedForAndRejectsUntrustedOrMultiValueDedicatedHeader() {
        MockHttpServletRequest xff = new MockHttpServletRequest();
        xff.setRemoteAddr("198.51.100.4");
        xff.addHeader("X-Forwarded-For", "203.0.113.10");
        assertThat(resolver.resolve(xff)).isEqualTo("198.51.100.4");

        MockHttpServletRequest untrusted = new MockHttpServletRequest();
        untrusted.setRemoteAddr("198.51.100.4");
        untrusted.addHeader("X-Gbsw-Client-IP", "203.0.113.10");
        assertThatThrownBy(() -> resolver.resolve(untrusted))
                .isInstanceOf(ClientAddressResolver.InvalidClientAddressException.class);

        MockHttpServletRequest multiple = new MockHttpServletRequest();
        multiple.setRemoteAddr("10.1.2.3");
        multiple.addHeader("X-Gbsw-Client-IP", "203.0.113.10, 203.0.113.11");
        assertThatThrownBy(() -> resolver.resolve(multiple))
                .isInstanceOf(ClientAddressResolver.InvalidClientAddressException.class);
    }

    private DeploymentSecurityProperties properties() {
        return new DeploymentSecurityProperties(
                false, "X-Gbsw-Client-IP", List.of("10.0.0.0/8"),
                100, Duration.ofMinutes(15), 30, 2,
                100, Duration.ofMinutes(15), Duration.ofHours(24),
                Duration.ofDays(30), 100, Duration.ofHours(1));
    }
}
