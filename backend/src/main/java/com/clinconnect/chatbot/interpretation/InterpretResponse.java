package com.clinconnect.chatbot.interpretation;

import com.clinconnect.chatbot.session.ClarificationParameterName;
import java.util.List;

/**
 * Mirrors ai-service's InterpretResponse (ai_service.interpretation.schemas
 * .InterpretResponse, Phase 2). {@code intentId} is deliberately a plain
 * String, not an enum: Spring already validates it against
 * config/intents.yaml via ChatbotConfigLoader (the single source of truth,
 * NFR-006) rather than duplicating a second intent-ID enum here.
 */
public record InterpretResponse(
        InterpretationStatus interpretationStatus,
        String intentId,
        InterpretationParameters parameters,
        List<ClarificationParameterName> missingParameters,
        ClarificationAnswer clarificationAnswer) {
}
