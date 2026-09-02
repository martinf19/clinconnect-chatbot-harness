package com.clinconnect.chatbot.chat;

import com.clinconnect.chatbot.session.ClarificationOption;
import com.clinconnect.chatbot.session.ClarificationParameterName;
import com.clinconnect.chatbot.session.ClarificationReason;
import java.util.List;

/** The frontend-facing shape of a pending clarification (docs/08-API-CONTRACTS.md). */
public record ClarificationView(
        ClarificationReason reason, ClarificationParameterName parameter, List<ClarificationOption> options) {
}
