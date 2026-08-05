package kr.hs.gbsw.communication.common.exception;

import jakarta.validation.ConstraintViolationException;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /**
     * 열거형이나 숫자 파라미터에 해석할 수 없는 값이 들어온 경우다.
     * 이 어드바이스가 Exception 전체를 받으므로 여기서 잡지 않으면 500으로 나간다.
     * 받은 값을 그대로 되돌려주지 않고 어떤 파라미터가 문제인지만 알린다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_FAILED",
                "요청 값을 확인해 주세요.",
                now(),
                traceId(),
                List.of(new FieldErrorResponse(exception.getName(), "허용되지 않는 값입니다."))));
    }

    /**
     * 컨트롤러 파라미터의 제약(@Min, @Max, @Size 등) 위반.
     *
     * `@Validated`가 붙은 컨트롤러는 AOP 경로를 타 ConstraintViolationException을,
     * 그렇지 않은 컨트롤러는 HandlerMethodValidationException을 던진다. 두 경로 모두
     * 여기서 잡지 않으면 500으로 나간다.
     */
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    ResponseEntity<ErrorResponse> handleParameterValidation(Exception exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                "VALIDATION_FAILED",
                "요청 값을 확인해 주세요.",
                now(),
                traceId()));
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
