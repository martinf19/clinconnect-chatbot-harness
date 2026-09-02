package com.clinconnect.chatbot.config.model;

import java.util.Map;

/**
 * A parsed entry from config/intents.yaml. Deliberately structural rather
 * than a full typed contract: Phase 0 only needs to validate and expose the
 * intent-to-tool mapping. Later phases may extend this without changing the
 * fail-closed loading behavior established here.
 */
public record IntentDefinition(String id, String toolId, Map<String, Object> raw) {
}
