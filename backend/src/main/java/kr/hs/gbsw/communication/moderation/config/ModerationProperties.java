package kr.hs.gbsw.communication.moderation.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.moderation")
public record ModerationProperties(
        /**
         * 신원 확인 사건이 승인된 뒤 고정 심의자가 신원을 열람할 수 있는 기간.
         * 짧게 설정할 수 있어야 만료 동작을 24시간을 기다리지 않고 검증할 수 있다.
         */
        @NotNull Duration identityRevealWindow
) {
}
