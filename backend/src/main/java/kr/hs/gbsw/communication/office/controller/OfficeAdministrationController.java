package kr.hs.gbsw.communication.office.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.common.security.TraceIdFilter;
import kr.hs.gbsw.communication.office.domain.OfficeType;
import kr.hs.gbsw.communication.office.dto.request.OfficeAppointmentRequest;
import kr.hs.gbsw.communication.user.dto.request.EndAssignmentRequest;
import kr.hs.gbsw.communication.user.dto.response.AssignmentPeriodResponse;
import kr.hs.gbsw.communication.user.service.AccountLifecycleService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/offices")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Office administration", description = "슈퍼 어드민 전용 임기 기반 보직 관리")
public class OfficeAdministrationController {

    private final AccountLifecycleService service;

    public OfficeAdministrationController(AccountLifecycleService service) {
        this.service = service;
    }

    @PostMapping("/{office}/appointments")
    @Operation(summary = "보직 임명·예약", description = "필요 역할과 기간 중복을 검증하고 후임 교체를 원자적으로 처리합니다.")
    public ResponseEntity<AssignmentPeriodResponse> appoint(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable OfficeType office,
            @Valid @RequestBody OfficeAppointmentRequest request
    ) {
        AssignmentPeriodResponse response = AssignmentPeriodResponse.from(service.appointOffice(
                actor,
                request.userPublicId(),
                office,
                request.startsAt(),
                request.endsAt(),
                request.replaceExistingAtStart(),
                request.reason(),
                traceId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{office}/users/{publicId}/end")
    @Operation(summary = "보직 임기 종료", description = "지정 사용자와 시각에 해당하는 보직 임기를 종료합니다.")
    public AssignmentPeriodResponse end(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable OfficeType office,
            @PathVariable java.util.UUID publicId,
            @Valid @RequestBody EndAssignmentRequest request
    ) {
        return AssignmentPeriodResponse.from(service.endOffice(
                actor, publicId, office, request.endsAt(), request.reason(), traceId()));
    }

    private String traceId() {
        String value = MDC.get(TraceIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
