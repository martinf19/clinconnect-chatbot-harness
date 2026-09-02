package com.clinconnect.chatbot.domain.model;

/**
 * Distinguishes the two intents that share the consult_routing_rule table
 * (docs/07-DATA-MODEL.md lists no separate Chart Chat table; FR-008 and
 * FR-010 share the same location/specialty/time_context argument shape per
 * config/tools.yaml).
 */
public enum ConsultRoutingCategory {
    CONSULT_ROUTING,
    CHART_CHAT_GUIDANCE
}
