package kr.hs.gbsw.communication.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.util.Optional;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.auth.domain.SessionAccountState;
import kr.hs.gbsw.communication.auth.repository.AuthRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountSessionValidationFilter extends OncePerRequestFilter {

    private final AuthRepository authRepository;
    private final SecurityErrorWriter errorWriter;
    private final Clock clock;

    public AccountSessionValidationFilter(
            AuthRepository authRepository,
            SecurityErrorWriter errorWriter,
            Clock clock
    ) {
        this.authRepository = authRepository;
        this.errorWriter = errorWriter;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<SessionAccountState> current = authRepository.findSessionState(principal.userId(), clock.instant());
        boolean valid = current
                .filter(SessionAccountState::active)
                .filter(state -> state.credentialVersion() == principal.credentialVersion())
                .filter(state -> state.authorities().equals(principal.authorities()))
                .isPresent();

        if (valid) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        errorWriter.write(
                response,
                HttpStatus.UNAUTHORIZED.value(),
                "SESSION_INVALIDATED",
                "계정 정보가 변경되어 다시 로그인해야 합니다.");
    }
}
