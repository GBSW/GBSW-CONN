package kr.hs.gbsw.communication.common.exception;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import kr.hs.gbsw.communication.common.response.ErrorResponse;
import kr.hs.gbsw.communication.common.response.FieldErrorResponse;
import kr.hs.gbsw.communication.common.security.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ErrorResponse.of(exception.code(), exception.getMessage(), now(), traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_FAILED",
                "요청 값을 확인해 주세요.",
                now(),
                traceId(),
                fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                "MALFORMED_REQUEST",
                "요청 본문 형식을 확인해 주세요.",
                now(),
                traceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                "ACCESS_DENIED",
                "이 작업을 수행할 권한이 없습니다.",
                now(),
                traceId()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled request failure; traceId={}", traceId(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(
                "INTERNAL_ERROR",
                "요청을 처리하지 못했습니다.",
                now(),
                traceId()));
    }

    private FieldErrorResponse toFieldError(FieldError error) {
        String message = error.getDefaultMessage() == null ? "값을 확인해 주세요." : error.getDefaultMessage();
        return new FieldErrorResponse(error.getField(), message);
    }

    private Instant now() {
        return clock.instant();
    }

    private String traceId() {
        String value = MDC.get(TraceIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
