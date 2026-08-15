package kr.hs.gbsw.communication.governance.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.common.security.TraceIdFilter;
import kr.hs.gbsw.communication.governance.dto.request.PrivilegedChangeApprovalRequest;
import kr.hs.gbsw.communication.governance.dto.request.PrivilegedChangeCreateRequest;
import kr.hs.gbsw.communication.governance.dto.response.PrivilegedChangeResponse;
import kr.hs.gbsw.communication.governance.service.PrivilegedChangeService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/governance/requests")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Validated
public class PrivilegedChangeController {

    private final PrivilegedChangeService service;

    public PrivilegedChangeController(PrivilegedChangeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrivilegedChangeResponse request(
            @AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody PrivilegedChangeCreateRequest request
    ) {
        return service.request(actor, request, traceId());
    }

    @GetMapping
    public List<PrivilegedChangeResponse> list(
            @AuthenticationPrincipal AuthPrincipal actor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return service.list(actor, size);
    }

    @PostMapping("/{publicId}/approve")
    public PrivilegedChangeResponse approve(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody PrivilegedChangeApprovalRequest request
    ) {
        return service.approve(actor, publicId, request, traceId());
    }

    private String traceId() {
        String value = MDC.get(TraceIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
