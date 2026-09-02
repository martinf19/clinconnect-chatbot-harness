package com.clinconnect.chatbot.format;

import com.clinconnect.chatbot.domain.model.ContactType;

/** Deterministic display labels for ContactType — no model-generated wording. */
final class ContactTypeLabels {

    private ContactTypeLabels() {
    }

    static String label(ContactType type) {
        return switch (type) {
            case MOBILE -> "Mobile";
            case OFFICE -> "Office";
            case TIE_LINE -> "Tie Line";
            case PAGER -> "Pager";
            case CHART_CHAT -> "Chart Chat";
            case BACKLINE -> "Backline";
        };
    }
}
