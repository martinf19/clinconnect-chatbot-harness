package com.clinconnect.chatbot.config.model;

import java.util.Map;

/**
 * The validated, loaded contents of config/intents.yaml and
 * config/tools.yaml: every intent's tool_id is guaranteed to resolve to a
 * tool in this map (see ChatbotConfigLoader).
 *
 * {@code specialtyAliases} is the config-driven alias map from intents.yaml
 * "aliases.specialty" (FR-015: aliases are configuration-driven, not
 * hardcoded in canonicalization logic).
 */
public record ChatbotIntentToolConfig(
        Map<String, IntentDefinition> intentsById,
        Map<String, ToolDefinition> toolsById,
        Map<String, String> specialtyAliases) {
}
