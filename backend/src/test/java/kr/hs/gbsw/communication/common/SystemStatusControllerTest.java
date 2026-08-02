package kr.hs.gbsw.communication.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.hs.gbsw.communication.common.response.SystemStatusController;
import kr.hs.gbsw.communication.common.response.SystemStatusResponse;
import org.junit.jupiter.api.Test;

class SystemStatusControllerTest {

    @Test
    void returnsServerControlledUtcTime() {
        Instant now = Instant.parse("2026-08-02T01:02:03Z");
        SystemStatusController controller = new SystemStatusController(Clock.fixed(now, ZoneOffset.UTC));

        SystemStatusResponse response = controller.status();

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.serverTime()).isEqualTo(now);
        assertThat(response.apiVersion()).isEqualTo("v1");
    }
}
