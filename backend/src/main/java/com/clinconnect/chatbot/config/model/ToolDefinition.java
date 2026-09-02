package com.clinconnect.chatbot.config.model;

import java.util.Map;

/** A parsed entry from config/tools.yaml. See IntentDefinition for scope notes. */
public record ToolDefinition(String id, String authorizationScope, Map<String, Object> raw) {
}
