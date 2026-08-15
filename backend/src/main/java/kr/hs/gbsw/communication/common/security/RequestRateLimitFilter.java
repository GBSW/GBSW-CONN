package kr.hs.gbsw.communication.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import kr.hs.gbsw.communication.common.config.DeploymentSecurityProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> AUTHENTICATION_PATHS = Set.of(
            "/api/v1/auth/activate",
            "/api/v1/auth/login",
            "/api/v1/auth/reauthenticate",
            "/api/v1/auth/password-reset/complete");

    private final ClientAddressResolver clientAddressResolver;
    private final SecurityErrorWriter errorWriter;
    private final Clock clock;
    private final BoundedTokenBucketRateLimiter generalLimiter;
    private final BoundedTokenBucketRateLimiter authenticationLimiter;

    public RequestRateLimitFilter(
            ClientAddressResolver clientAddressResolver,
            SecurityErrorWriter errorWriter,
            Clock clock,
            ApplicationSecurityProperties securityProperties,
            DeploymentSecurityProperties deploymentProperties
    ) {
        this.clientAddressResolver = clientAddressResolver;
        this.errorWriter = errorWriter;
        this.clock = clock;
        this.generalLimiter = new BoundedTokenBucketRateLimiter(
                securityProperties.rateLimit().generalRequestsPerMinute(),
                deploymentProperties.requestLimiterMaxEntries(),
                deploymentProperties.requestLimiterIdleTtl());
        this.authenticationLimiter = new BoundedTokenBucketRateLimiter(
                deploymentProperties.authenticationRequestsPerMinute(),
                deploymentProperties.requestLimiterMaxEntries(),
                deploymentProperties.requestLimiterIdleTtl());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String clientAddress;
        try {
            clientAddress = clientAddressResolver.resolve(request);
        } catch (ClientAddressResolver.InvalidClientAddressException exception) {
            errorWriter.write(
                    response,
                    HttpStatus.BAD_REQUEST.value(),
                    "INVALID_CLIENT_ADDRESS",
                    "클라이언트 주소 정보를 확인할 수 없습니다.");
            return;
        }

        Instant now = clock.instant();
        if (!generalLimiter.tryAcquire(clientAddress, now)
                || isAuthenticationRequest(request, path)
                && !authenticationLimiter.tryAcquire(clientAddress, now)) {
            response.setHeader("Retry-After", "60");
            errorWriter.write(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "REQUEST_RATE_LIMITED",
                    "요청이 너무 많습니다. 잠시 후 다시 시도하세요.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAuthenticationRequest(HttpServletRequest request, String path) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return AUTHENTICATION_PATHS.contains(path)
                || path.startsWith("/api/v1/identity-reveal-cases/") && path.endsWith("/reveal");
    }
}
