package com.clinconnect.chatbot.interpretation;

import com.clinconnect.chatbot.time.TimeExpressionKind;
import java.time.LocalDate;

/** Mirrors ai-service's TimeExpression (docs/05-INTENT-CATALOG.md). */
public record TimeExpressionValue(TimeExpressionKind kind, LocalDate specificDate) {
}
