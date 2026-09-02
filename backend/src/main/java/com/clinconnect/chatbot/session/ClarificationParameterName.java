package com.clinconnect.chatbot.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The canonical interpretation-parameter key being clarified
 * (docs/08-API-CONTRACTS.md "Clarification Parameter Names"). Wire values
 * are the exact lower-snake-case strings from that list, shared verbatim
 * with the ai-service contract (ai_service.interpretation.enums
 * .ClarificationParameterName) so a value round-trips unchanged between
 * Python's {@code missing_parameters}/{@code pending_clarification
 * .parameter} and Spring's own {@code clarification.parameter}.
 */
public enum ClarificationParameterName {
    LOCATION_TEXT("location_text"),
    SPECIALTY_TEXT("specialty_text"),
    PROVIDER_REFERENCE("provider_reference"),
    ROLE_TEXT("role_text"),
    CONTACT_TYPE("contact_type"),
    TIME_EXPRESSION("time_expression"),
    TIME_CONTEXT("time_context"),
    DECLARED_URGENCY("declared_urgency");

    private final String wireValue;

    ClarificationParameterName(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    private static final Map<String, ClarificationParameterName> BY_WIRE_VALUE = java.util.Arrays.stream(values())
            .collect(Collectors.toMap(ClarificationParameterName::wireValue, v -> v));

    @JsonCreator
    public static ClarificationParameterName fromWireValue(String wireValue) {
        ClarificationParameterName match = BY_WIRE_VALUE.get(wireValue);
        if (match == null) {
            throw new IllegalArgumentException("Unknown clarification parameter: " + wireValue);
        }
        return match;
    }
}
