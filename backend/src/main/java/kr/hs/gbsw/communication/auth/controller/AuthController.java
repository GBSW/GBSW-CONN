package kr.hs.gbsw.communication.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.auth.dto.request.ActivationRequest;
import kr.hs.gbsw.communication.auth.dto.request.LoginRequest;
import kr.hs.gbsw.communication.auth.dto.request.PasswordResetRequest;
import kr.hs.gbsw.communication.auth.dto.request.ReauthenticationRequest;
import kr.hs.gbsw.communication.auth.dto.response.CsrfTokenResponse;
import kr.hs.gbsw.communication.auth.dto.response.CurrentUserResponse;
import kr.hs.gbsw.communication.auth.service.AuthService;
import kr.hs.gbsw.communication.common.security.TraceIdFilter;
import kr.hs.gbsw.communication.common.response.ErrorResponse;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "계정 활성화, 자체 로그인과 MySQL 세션")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final ApplicationSecurityProperties properties;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthController(
            AuthService authService,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            ApplicationSecurityProperties properties
    ) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.properties = properties;
    }

    @GetMapping("/csrf")
    @Operation(summary = "CSRF 토큰 발급", description = "로그인 전을 포함해 상태 변경 요청 전에 호출합니다.")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return CsrfTokenResponse.from(csrfToken);
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "최초 계정 활성화", description = "개인별 일회용 가입 코드를 소비하고 Argon2id 비밀번호를 설정합니다.")
    @ApiResponse(responseCode = "204", description = "활성화 완료")
    public void activate(
            @Valid @RequestBody ActivationRequest request,
            HttpServletRequest servletRequest
    ) {
        authService.activate(
                request.loginId(),
                request.activationCode(),
                request.password(),
                remoteAddress(servletRequest),
                traceId());
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "계정/IP 영속 제한을 확인하고 새 MySQL 세션을 발급합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공과 새 서버 세션 발급"),
            @ApiResponse(responseCode = "400", description = "입력 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "자격 증명 또는 계정 상태 확인 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "계정 또는 IP 로그인 제한",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CurrentUserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthPrincipal principal = authService.login(
                request.loginId(),
                request.password(),
                remoteAddress(servletRequest),
                traceId());

        HttpSession existingSession = servletRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }

        savePrincipal(principal, servletRequest, servletResponse);
        csrfTokenRepository.saveToken(null, servletRequest, servletResponse);
        return currentUserResponse(principal);
    }

    @PostMapping("/reauthenticate")
    @Operation(summary = "최근 재인증", description = "민감한 관리 작업 전에 현재 비밀번호를 다시 확인합니다.")
    public CurrentUserResponse reauthenticate(
            @AuthenticationPrincipal AuthPrincipal currentPrincipal,
            @Valid @RequestBody ReauthenticationRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthPrincipal principal = authService.reauthenticate(
                currentPrincipal,
                request.password(),
                remoteAddress(servletRequest),
                traceId());
        savePrincipal(principal, servletRequest, servletResponse);
        return currentUserResponse(principal);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "로그아웃", description = "현재 서버 세션과 세션·CSRF 쿠키를 무효화합니다.")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var authentication = contextHolderStrategy.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler("SESSION").logout(request, response, authentication);
        csrfTokenRepository.saveToken(null, request, response);
    }

    @GetMapping("/me")
    @Operation(summary = "현재 사용자", description = "세션에 연결된 공개 계정 정보와 현재 역할·보직을 반환합니다.")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return currentUserResponse(principal);
    }

    @PostMapping("/password-reset/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "비밀번호 재설정 완료", description = "일회용 재설정 코드를 소비하고 기존 세션을 모두 폐기합니다.")
    public void resetPassword(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest servletRequest
    ) {
        authService.resetPassword(
                request.loginId(),
                request.resetCode(),
                request.newPassword(),
                remoteAddress(servletRequest),
                traceId());
    }

    private String remoteAddress(HttpServletRequest request) {
        String value = request.getRemoteAddr();
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String traceId() {
        String value = MDC.get(TraceIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }

    private void savePrincipal(
            AuthPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        List<SimpleGrantedAuthority> authorities = principal.authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private CurrentUserResponse currentUserResponse(AuthPrincipal principal) {
        return CurrentUserResponse.from(principal, properties.credentials().reauthenticationTtl());
    }
}
