package kr.hs.gbsw.communication.proposal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.common.security.TraceIdFilter;
import kr.hs.gbsw.communication.proposal.dto.request.AssignProposalTeacherRequest;
import kr.hs.gbsw.communication.proposal.dto.response.AdminProposalSummaryResponse;
import kr.hs.gbsw.communication.proposal.dto.response.EligibleProposalTeacherResponse;
import kr.hs.gbsw.communication.proposal.dto.response.ProposalAssignmentResponse;
import kr.hs.gbsw.communication.proposal.service.ProposalAssignmentService;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/proposals")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Validated
@Tag(name = "Proposal administration", description = "슈퍼 어드민 전용 정식 안건 담당 교사 내부 지정")
public class ProposalAdministrationController {

    private final ProposalAssignmentService service;

    public ProposalAdministrationController(ProposalAssignmentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "담당 지정용 정식 안건 목록", description = "본문 없이 제목·상태와 내부 담당 정보만 반환합니다.")
    public List<AdminProposalSummaryResponse> list(
            @AuthenticationPrincipal AuthPrincipal actor,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return service.list(actor, query, size).stream()
                .map(AdminProposalSummaryResponse::from)
                .toList();
    }

    @GetMapping("/eligible-teachers")
    @Operation(summary = "담당 지정 가능한 활성 교사 목록")
    public List<EligibleProposalTeacherResponse> teachers(
            @AuthenticationPrincipal AuthPrincipal actor,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return service.teachers(actor, query, size).stream()
                .map(EligibleProposalTeacherResponse::from)
                .toList();
    }

    @PostMapping("/{publicId}/assignments")
    @Operation(summary = "정식 안건 담당 교사 지정", description = "기존 담당은 종료 이력으로 보존하고 새 담당을 내부 지정합니다.")
    public ProposalAssignmentResponse assign(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody AssignProposalTeacherRequest request
    ) {
        return ProposalAssignmentResponse.from(service.assign(
                actor, publicId, request.teacherPublicId(), request.reason(), traceId()));
    }

    private String traceId() {
        String value = MDC.get(TraceIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
