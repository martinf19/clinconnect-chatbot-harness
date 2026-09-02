package com.clinconnect.chatbot.correlation;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Spring is the only component authorized to mint the authoritative
 * correlation ID for a request (CLAUDE.md: Spring Boot owns correlation IDs).
 */
@Component
public class CorrelationIdProvider {

    public String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
