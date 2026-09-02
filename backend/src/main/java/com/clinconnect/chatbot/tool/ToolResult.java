package com.clinconnect.chatbot.tool;

import java.util.List;

/**
 * The result of a deterministic tool execution. {@code ambiguousCandidateIds}
 * is populated only for {@link ToolResultStatus#AMBIGUOUS}, so Spring's
 * (later-phase) clarification flow has canonical IDs to offer rather than
 * inventing them.
 */
public record ToolResult<T>(ToolResultStatus status, T data, List<String> ambiguousCandidateIds) {

    public static <T> ToolResult<T> found(T data) {
        return new ToolResult<>(ToolResultStatus.FOUND, data, List.of());
    }

    public static <T> ToolResult<T> noMatch() {
        return new ToolResult<>(ToolResultStatus.NO_MATCH, null, List.of());
    }

    public static <T> ToolResult<T> ambiguous(List<String> candidateIds) {
        return new ToolResult<>(ToolResultStatus.AMBIGUOUS, null, List.copyOf(candidateIds));
    }
}
