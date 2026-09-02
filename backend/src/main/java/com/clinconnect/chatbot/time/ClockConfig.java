package com.clinconnect.chatbot.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single trusted source of "now" for the backend (FR-004/FR-005: Spring
 * supplies the reference instant, never the model). Exposed as an
 * injectable bean so tests can substitute a fixed Clock instead of the
 * workstation wall clock (docs/10-EVALUATION-PLAN.md).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
