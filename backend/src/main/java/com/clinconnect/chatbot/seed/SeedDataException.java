package com.clinconnect.chatbot.seed;

/**
 * Thrown when a {@code config/seed-data/*.csv} file is missing, malformed,
 * or has a row that fails to parse. Deployment/configuration errors of this
 * kind must fail closed (same principle as {@code ChatbotConfigException}
 * for {@code intents.yaml}/{@code tools.yaml}, docs/09-SECURITY.md) rather
 * than let the app run with partial or inconsistent seeded domain data.
 */
public class SeedDataException extends RuntimeException {

    public SeedDataException(String message) {
        super(message);
    }
}
