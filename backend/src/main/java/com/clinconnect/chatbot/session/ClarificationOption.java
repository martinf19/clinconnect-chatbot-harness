package com.clinconnect.chatbot.session;

/**
 * One offered clarification choice. {@code optionId} is always a canonical
 * ID Spring already resolved (FR-012 "options come only from
 * Spring/backend data/configuration") — never model-invented.
 */
public record ClarificationOption(String optionId, String label) {
}
