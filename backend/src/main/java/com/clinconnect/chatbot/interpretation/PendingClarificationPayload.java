package com.clinconnect.chatbot.interpretation;

import com.clinconnect.chatbot.session.ClarificationOption;
import com.clinconnect.chatbot.session.ClarificationParameterName;
import java.util.List;

/** What Spring tells Python about the clarification currently pending. */
public record PendingClarificationPayload(
        String reason, ClarificationParameterName parameter, List<ClarificationOption> options) {
}
