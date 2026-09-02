package com.clinconnect.chatbot.interpretation;

/** Mirrors ai-service's ProviderReference (docs/05-INTENT-CATALOG.md). */
public record ProviderReferenceValue(ProviderReferenceKind kind, String text) {
}
