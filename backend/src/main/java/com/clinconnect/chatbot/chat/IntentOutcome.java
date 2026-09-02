package com.clinconnect.chatbot.chat;

import com.clinconnect.chatbot.session.ClarificationOption;
import com.clinconnect.chatbot.session.ClarificationParameterName;
import com.clinconnect.chatbot.session.ClarificationReason;
import com.clinconnect.chatbot.session.LastResultContext;
import java.util.List;

/**
 * The result of running one canonical intent's deterministic handler
 * (IntentOrchestrationService) — after Python's language interpretation,
 * before Spring formats the final chatbot response. UNSUPPORTED/ERROR are
 * decided earlier in ChatOrchestrationService and never reach this type.
 */
public sealed interface IntentOutcome {

    record Answer(String text, LastResultContext newLastResultContext, ResolvedQueryContext resolvedContext)
            implements IntentOutcome {

        public Answer(String text, LastResultContext newLastResultContext) {
            this(text, newLastResultContext, ResolvedQueryContext.EMPTY);
        }
    }

    record Clarify(
            ClarificationReason reason, ClarificationParameterName parameter, List<ClarificationOption> options)
            implements IntentOutcome {
    }

    record NoMatch(ResolvedQueryContext resolvedContext) implements IntentOutcome {

        public NoMatch() {
            this(ResolvedQueryContext.EMPTY);
        }
    }
}
