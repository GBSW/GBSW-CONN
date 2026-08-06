package kr.hs.gbsw.communication.moderation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.common.security.TraceIdFilter;
import kr.hs.gbsw.communication.moderation.domain.ModerationVoteDecision;
import kr.hs.gbsw.communication.moderation.dto.request.CreateModerationCaseRequest;
import kr.hs.gbsw.communication.moderation.dto.request.ModerationVoteRequest;
import kr.hs.gbsw.communication.moderation.dto.response.ModerationCaseResponse;
import kr.hs.gbsw.communication.moderation.dto.response.ModerationVoteResultResponse;
import kr.hs.gbsw.communication.moderation.dto.response.ReportInboxItemResponse;
import kr.hs.gbsw.communication.moderation.service.ModerationCaseService;
import kr.hs.gbsw.communication.moderation.service.ModerationVoteService;
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
@RequestMapping("/api/v1/moderation")
@PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
@Validated
@Tag(name = "Moderation", description = "신고 분리 심의와 3인 고정 심의")
public class ModerationController {

    private final ModerationCaseService caseService;
    private final ModerationVoteService voteService;

    public ModerationController(
            ModerationCaseService caseService,
            ModerationVoteService voteService
    ) {
        this.caseService = caseService;
        this.voteService = voteService;
    }

    @GetMapping("/reports")
    @Operation(summary = "신고 사건함", description = "현임 학생부장교사에게만 신고 내용과 제안 본문을 제공합니다.")
    public List<ReportInboxItemResponse> reports(
            @AuthenticationPrincipal AuthPrincipal actor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return caseService.reports(actor, size).stream()
                .map(ReportInboxItemResponse::from)
                .toList();
    }

    @PostMapping("/reports/{reportPublicId}/cases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "분리 심의 사건 생성", description = "현임 학생부장교사가 현재 세 직책 담당자를 사건 심의자로 고정합니다.")
    public ModerationCaseResponse createCase(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID reportPublicId,
            @Valid @RequestBody CreateModerationCaseRequest request
    ) {
        return ModerationCaseResponse.from(caseService.create(
                actor, reportPublicId, request.caseType(), request.reason(), traceId()));
    }

    @GetMapping("/cases")
    @Operation(summary = "내 심의 사건 목록", description = "사건 생성 시 고정된 심의자에게만 해당 사건을 제공합니다.")
    public List<ModerationCaseResponse> cases(
            @AuthenticationPrincipal AuthPrincipal actor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return caseService.cases(actor, size).stream()
                .map(ModerationCaseResponse::from)
                .toList();
    }

    @GetMapping("/cases/{publicId}")
    @Operation(summary = "심의 사건 상세")
    public ModerationCaseResponse caseDetail(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId
    ) {
        return ModerationCaseResponse.from(caseService.caseDetail(actor, publicId));
    }

    @PostMapping("/cases/{publicId}/votes/approve")
    @Operation(summary = "심의 승인", description = "세 명 모두 승인하면 사건이 승인됩니다.")
    public ModerationVoteResultResponse approve(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody ModerationVoteRequest request
    ) {
        return vote(actor, publicId, ModerationVoteDecision.APPROVE, request.reason());
    }

    @PostMapping("/cases/{publicId}/votes/reject")
    @Operation(summary = "심의 반대", description = "한 명이라도 반대하면 사건이 반려됩니다.")
    public ModerationVoteResultResponse reject(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody ModerationVoteRequest request
    ) {
        return vote(actor, publicId, ModerationVoteDecision.REJECT, request.reason());
    }

    private ModerationVoteResultResponse vote(
            AuthPrincipal actor,
            UUID publicId,
            ModerationVoteDecision decision,
            String reason
    ) {
        return new ModerationVoteResultResponse(
                voteService.vote(actor, publicId, decision, reason, traceId()).name());
    }

    private String traceId() {
        String value = MDC.get(TraceIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
