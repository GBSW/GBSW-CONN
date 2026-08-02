package kr.hs.gbsw.communication.proposal.controller;

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
import kr.hs.gbsw.communication.moderation.domain.ContentReportResult;
import kr.hs.gbsw.communication.moderation.dto.request.ContentReportRequest;
import kr.hs.gbsw.communication.moderation.dto.response.ContentReportResponse;
import kr.hs.gbsw.communication.moderation.service.ContentReportService;
import kr.hs.gbsw.communication.proposal.domain.ProposalFeedScope;
import kr.hs.gbsw.communication.proposal.domain.ProposalSort;
import kr.hs.gbsw.communication.proposal.dto.request.CreateProposalRequest;
import kr.hs.gbsw.communication.proposal.dto.request.CreateProposalCommentRequest;
import kr.hs.gbsw.communication.proposal.dto.request.UpdateProposalRequest;
import kr.hs.gbsw.communication.proposal.dto.request.OfficialResponseRequest;
import kr.hs.gbsw.communication.proposal.dto.request.ProposalTransitionReasonRequest;
import kr.hs.gbsw.communication.proposal.dto.response.ProposalDetailResponse;
import kr.hs.gbsw.communication.proposal.dto.response.ProposalCommentResponse;
import java.util.List;
import kr.hs.gbsw.communication.proposal.dto.response.ProposalPageResponse;
import kr.hs.gbsw.communication.proposal.dto.response.SupportResponse;
import kr.hs.gbsw.communication.proposal.dto.response.ProposalWorkflowResponse;
import kr.hs.gbsw.communication.proposal.service.ProposalService;
import kr.hs.gbsw.communication.proposal.service.ProposalWorkflowService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/proposals")
@Validated
@Tag(name = "Public proposals", description = "학생 공개 제안과 동의, 정식 안건 승격")
public class ProposalController {

    private final ProposalService service;
    private final ProposalWorkflowService workflowService;
    private final ContentReportService contentReportService;

    public ProposalController(
            ProposalService service,
            ProposalWorkflowService workflowService,
            ContentReportService contentReportService
    ) {
        this.service = service;
        this.workflowService = workflowService;
        this.contentReportService = contentReportService;
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "공개 제안 등록", description = "익명 또는 실명 공개를 선택하고 작성자 자동 동의 1표를 함께 저장합니다.")
    @ApiResponse(responseCode = "201", description = "제안과 첫 동의 생성")
    public ResponseEntity<ProposalDetailResponse> create(
            @AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody CreateProposalRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(
                actor, request.title(), request.content(), request.authorVisibility(), traceId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    @Operation(summary = "제안 피드", description = "학생은 공개 제안을, 교사는 정식 안건 이후 제안만 조회합니다.")
    public ProposalPageResponse list(
            @AuthenticationPrincipal AuthPrincipal viewer,
            @RequestParam(defaultValue = "ALL") ProposalFeedScope scope,
            @RequestParam(defaultValue = "LATEST") ProposalSort sort,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return service.list(viewer, scope, sort, query, page, size);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    @Operation(summary = "제안 상세", description = "교사에게는 정식 안건 이후 제안만 존재하는 것으로 공개합니다.")
    public ProposalDetailResponse get(
            @AuthenticationPrincipal AuthPrincipal viewer,
            @PathVariable UUID publicId
    ) {
        return service.get(viewer, publicId);
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "본인 제안 수정", description = "동의 모집 중인 제안의 작성자만 제목과 내용을 수정할 수 있습니다.")
    public ProposalDetailResponse update(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody UpdateProposalRequest request
    ) {
        return service.update(actor, publicId, request.title(), request.content(), traceId());
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "본인 제안 철회", description = "동의 모집 중인 본인 제안을 공개 목록에서 철회하며 기록은 보존합니다.")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId
    ) {
        service.withdraw(actor, publicId, traceId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{publicId}/comments")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    @Operation(summary = "제안 댓글 목록", description = "제안을 볼 수 있는 사용자가 학생 댓글을 시간순으로 조회합니다.")
    public List<ProposalCommentResponse> listComments(
            @AuthenticationPrincipal AuthPrincipal viewer,
            @PathVariable UUID publicId
    ) {
        return service.listComments(viewer, publicId);
    }

    @PostMapping("/{publicId}/comments")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "제안 댓글 작성", description = "활성 학생이 공개 중인 제안에 댓글을 작성합니다.")
    public ResponseEntity<ProposalCommentResponse> createComment(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody CreateProposalCommentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.createComment(actor, publicId, request.content(), traceId()));
    }

    @DeleteMapping("/{publicId}/comments/{commentPublicId}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "본인 댓글 삭제", description = "댓글 작성자만 자신의 댓글을 공개 목록에서 삭제할 수 있습니다.")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @PathVariable UUID commentPublicId
    ) {
        service.deleteComment(actor, publicId, commentPublicId, traceId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{publicId}/reports")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    @Operation(summary = "제안 신고", description = "같은 사용자의 같은 제안 신고는 멱등 처리하며 신고만으로 공개 상태는 바뀌지 않습니다.")
    public ResponseEntity<ContentReportResponse> report(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody ContentReportRequest request
    ) {
        ContentReportResult result = contentReportService.report(
                actor, publicId, request.reason(), traceId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ContentReportResponse.from(result.receipt()));
    }

    @PutMapping("/{publicId}/support")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "제안 동의", description = "중복 요청을 멱등 처리하고 DB 유효 동의 수로 정식 안건 승격을 판단합니다.")
    public SupportResponse support(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId
    ) {
        return SupportResponse.from(
                service.support(actor, publicId, traceId()),
                service.supportThreshold());
    }

    @DeleteMapping("/{publicId}/support")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "제안 동의 철회", description = "동의 모집 중에만 본인의 동의를 멱등적으로 철회합니다.")
    public SupportResponse withdrawSupport(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId
    ) {
        return SupportResponse.from(
                service.withdrawSupport(actor, publicId),
                service.supportThreshold());
    }

    @PostMapping("/{publicId}/review-start")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "담당 교사 검토 시작")
    public ProposalWorkflowResponse startReview(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody ProposalTransitionReasonRequest request
    ) {
        return ProposalWorkflowResponse.from(
                workflowService.startReview(actor, publicId, request.reason(), traceId()));
    }

    @PostMapping("/{publicId}/review-resume")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "보류 안건 검토 재개")
    public ProposalWorkflowResponse resumeReview(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody ProposalTransitionReasonRequest request
    ) {
        return ProposalWorkflowResponse.from(
                workflowService.resumeReview(actor, publicId, request.reason(), traceId()));
    }

    @PostMapping("/{publicId}/decisions/accept")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "정식 안건 채택과 공식 답변")
    public ProposalWorkflowResponse accept(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody OfficialResponseRequest request
    ) {
        return ProposalWorkflowResponse.from(workflowService.accept(
                actor, publicId, request.content(), request.decisionReason(), request.followUpPlan(), traceId()));
    }

    @PostMapping("/{publicId}/decisions/hold")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "정식 안건 보류와 공식 답변")
    public ProposalWorkflowResponse hold(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody OfficialResponseRequest request
    ) {
        return ProposalWorkflowResponse.from(workflowService.hold(
                actor, publicId, request.content(), request.decisionReason(), request.followUpPlan(), traceId()));
    }

    @PostMapping("/{publicId}/decisions/reject")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "정식 안건 반려와 공식 답변")
    public ProposalWorkflowResponse reject(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody OfficialResponseRequest request
    ) {
        return ProposalWorkflowResponse.from(workflowService.reject(
                actor, publicId, request.content(), request.decisionReason(), request.followUpPlan(), traceId()));
    }

    @PostMapping("/{publicId}/execution-start")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "채택 안건 실행 시작과 공식 업데이트")
    public ProposalWorkflowResponse startExecution(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody OfficialResponseRequest request
    ) {
        return ProposalWorkflowResponse.from(workflowService.startExecution(
                actor, publicId, request.content(), request.decisionReason(), request.followUpPlan(), traceId()));
    }

    @PostMapping("/{publicId}/execution-complete")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "안건 실행 완료와 공식 업데이트")
    public ProposalWorkflowResponse completeExecution(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable UUID publicId,
            @Valid @RequestBody OfficialResponseRequest request
    ) {
        return ProposalWorkflowResponse.from(workflowService.completeExecution(
                actor, publicId, request.content(), request.decisionReason(), request.followUpPlan(), traceId()));
    }

    private String traceId() {
        String value = MDC.get(TraceIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
