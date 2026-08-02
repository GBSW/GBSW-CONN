package kr.hs.gbsw.communication.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.common.security.TraceIdFilter;
import kr.hs.gbsw.communication.user.dto.request.CreateAccountRequest;
import kr.hs.gbsw.communication.user.dto.request.EndAssignmentRequest;
import kr.hs.gbsw.communication.user.dto.request.ReasonRequest;
import kr.hs.gbsw.communication.user.dto.request.RoleAssignmentRequest;
import kr.hs.gbsw.communication.user.dto.response.AssignmentPeriodResponse;
import kr.hs.gbsw.communication.user.dto.response.AccountDetailResponse;
import kr.hs.gbsw.communication.user.dto.response.AccountPageResponse;
import kr.hs.gbsw.communication.user.dto.response.OneTimeCodeResponse;
import kr.hs.gbsw.communication.user.domain.AccountRole;
import kr.hs.gbsw.communication.user.domain.AccountStatus;
import kr.hs.gbsw.communication.user.service.AccountLifecycleService;
import kr.hs.gbsw.communication.user.service.AccountAdministrationReadService;
import kr.hs.gbsw.communication.user.service.UserAdministrationService;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Validated
@Tag(name = "User administration", description = "슈퍼 어드민 전용 계정과 일회용 코드 관리")
public class UserAdministrationController {

    private final UserAdministrationService service;
    private final AccountLifecycleService lifecycleService;
    private final AccountAdministrationReadService readService;

    public UserAdministrationController(
            UserAdministrationService service,
            AccountLifecycleService lifecycleService,
            AccountAdministrationReadService readService
    ) {
        this.service = service;
        this.lifecycleService = lifecycleService;
        this.readService = readService;
    }

    @GetMapping
    @Operation(summary = "계정 검색", description = "로그인 ID·표시 이름과 상태로 계정을 검색하고 현재 역할·보직을 조회합니다.")
    public AccountPageResponse list(
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return readService.list(query, status, page, size);
    }

    @PostMapping
    @Operation(summary = "계정 생성", description = "활성화 대기 계정과 역할을 만들고 가입 코드를 한 번만 반환합니다.")
    @ApiResponse(responseCode = "201", description = "계정과 가입 코드 생성")
    public ResponseEntity<OneTimeCodeResponse> create(
            @AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody CreateAccountRequest request
    ) {
        OneTimeCodeResponse response = OneTimeCodeResponse.from(service.createAccount(
                actor,
                request.loginId(),
                request.displayName(),
                request.role(),
                request.reason(),
                traceId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "계정 상세", description = "계정 상태와 덮어쓰지 않은 역할·보직 임기 이력을 조회합니다.")
    public AccountDetailResponse get(@PathVariable UUID publicId) {
        return readService.get(publicId);
    }

    @PostMapping("/{publicId}/activation-code")
    @Operation(summary = "가입 코드 재발급", description = "이전 미사용 코드를 폐기하고 새 코드를 한 번만 반환합니다.")
    public ResponseEntity<OneTimeCodeResponse> reissueActivationCode(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId
    ) {
        OneTimeCodeResponse response = OneTimeCodeResponse.from(
                service.reissueActivationCode(actor, publicId, traceId()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping("/{publicId}/password-reset-code")
    @Operation(summary = "비밀번호 재설정 코드 발급", description = "이전 미사용 코드를 폐기하고 새 코드를 한 번만 반환합니다.")
    public ResponseEntity<OneTimeCodeResponse> issuePasswordResetCode(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId
    ) {
        OneTimeCodeResponse response = OneTimeCodeResponse.from(
                service.issuePasswordResetCode(actor, publicId, traceId()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping("/{publicId}/suspensions")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "계정 정지", description = "활성 계정을 정지하고 해당 계정의 모든 서버 세션을 폐기합니다.")
    public void suspend(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody ReasonRequest request
    ) {
        lifecycleService.suspend(actor, publicId, request.reason(), traceId());
    }

    @PostMapping("/{publicId}/reactivations")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "계정 재활성화", description = "정지 계정을 기존 자격 증명으로 다시 활성화합니다.")
    public void reactivate(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody ReasonRequest request
    ) {
        lifecycleService.reactivate(actor, publicId, request.reason(), traceId());
    }

    @PostMapping("/{publicId}/roles")
    @Operation(summary = "역할 임기 추가", description = "기존 이력을 덮어쓰지 않고 기간이 겹치지 않는 역할 임기를 추가합니다.")
    public ResponseEntity<AssignmentPeriodResponse> assignRole(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody RoleAssignmentRequest request
    ) {
        AssignmentPeriodResponse response = AssignmentPeriodResponse.from(lifecycleService.assignRole(
                actor,
                publicId,
                request.role(),
                request.startsAt(),
                request.endsAt(),
                request.reason(),
                traceId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{publicId}/roles/{role}/end")
    @Operation(summary = "역할 임기 종료", description = "지정 시각을 포함하는 역할 임기를 종료하고 변경 사유를 보존합니다.")
    public AssignmentPeriodResponse endRole(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @PathVariable AccountRole role,
            @Valid @RequestBody EndAssignmentRequest request
    ) {
        return AssignmentPeriodResponse.from(lifecycleService.endRole(
                actor, publicId, role, request.endsAt(), request.reason(), traceId()));
    }

    private String traceId() {
        String value = MDC.get(TraceIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
