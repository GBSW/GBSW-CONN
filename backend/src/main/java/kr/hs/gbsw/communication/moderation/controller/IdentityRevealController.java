package kr.hs.gbsw.communication.moderation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.common.security.TraceIdFilter;
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
@Tag(name = "Identity reveal", description = "승인된 사건의 일회성 작성자 신원 확인")
public class IdentityRevealController {

    private final IdentityRevealService service;

    public IdentityRevealController(IdentityRevealService service) {
        this.service = service;
    }

    @PostMapping("/{publicId}/reveal")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "작성자 신원 일회 확인", description = "승인 사건의 학생부장교사가 최근 재인증 후 한 번만 확인할 수 있습니다.")
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
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
