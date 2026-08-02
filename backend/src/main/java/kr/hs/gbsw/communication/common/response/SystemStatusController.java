package kr.hs.gbsw.communication.common.response;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.Clock;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/system", produces = MediaType.APPLICATION_JSON_VALUE)
public class SystemStatusController {

    private final Clock clock;

    public SystemStatusController(Clock clock) {
        this.clock = clock;
    }

    @Operation(
            summary = "API 상태 확인",
            description = "인증 없이 API 프로세스와 서버 기준 시각을 확인합니다. 데이터베이스 준비 상태는 운영용 health 상세 정책과 분리합니다.")
    @ApiResponse(responseCode = "200", description = "API가 요청을 처리할 수 있음")
    @GetMapping("/status")
    public SystemStatusResponse status() {
        return new SystemStatusResponse("ok", clock.instant(), "v1");
    }
}
