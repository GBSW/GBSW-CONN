package kr.hs.gbsw.communication.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
