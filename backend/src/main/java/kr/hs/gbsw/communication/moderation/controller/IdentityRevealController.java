package kr.hs.gbsw.communication.moderation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.common.security.TraceIdFilter;
import kr.hs.gbsw.communication.common.security.ClientAddressResolver;
import kr.hs.gbsw.communication.moderation.dto.request.IdentityRevealRequest;
import kr.hs.gbsw.communication.moderation.dto.response.IdentityRevealResponse;
import kr.hs.gbsw.communication.moderation.service.IdentityRevealService;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity-reveal-cases")
@Tag(name = "Identity reveal", description = "승인된 사건의 기간 제한 작성자 신원 확인")
public class IdentityRevealController {

    private final IdentityRevealService service;
    private final ClientAddressResolver clientAddressResolver;

    public IdentityRevealController(
            IdentityRevealService service,
            ClientAddressResolver clientAddressResolver
    ) {
        this.service = service;
        this.clientAddressResolver = clientAddressResolver;
    }

    // 열람 자격은 역할이 아니라 사건에 고정된 심의자인지로 정해진다. 학생회장과
    // 부회장은 학생 역할이므로 여기서 교사 역할을 요구하면 명세를 만족할 수 없다.
    // 실제 심의자 확인과 기간 판정은 서비스가 담당한다.
    @PostMapping("/{publicId}/reveal")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    @Operation(summary = "작성자 신원 확인",
            description = "전원 승인된 사건의 고정 심의자가 승인 시점부터 정해진 기간 동안 최근 재인증 후 확인합니다.")
    public ResponseEntity<IdentityRevealResponse> reveal(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody IdentityRevealRequest request,
            HttpServletRequest servletRequest
    ) {
        IdentityRevealResponse response = IdentityRevealResponse.from(
                service.reveal(
                        actor, publicId, request.reason(), remoteAddress(servletRequest), traceId()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    private String traceId() {
        String value = MDC.get(TraceIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }

    private String remoteAddress(HttpServletRequest request) {
        return clientAddressResolver.resolve(request);
    }
}
