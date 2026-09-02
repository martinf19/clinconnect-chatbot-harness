package com.clinconnect.chatbot.interpretation;

import com.clinconnect.chatbot.domain.model.ContactType;
import com.clinconnect.chatbot.domain.model.DeclaredUrgency;
import com.clinconnect.chatbot.domain.model.TimeContext;

/**
 * The canonical parameter vocabulary (docs/05-INTENT-CATALOG.md), mirroring
 * ai-service's InterpretationParameters. All raw text; Spring owns
 * canonicalization (FR-015). Stored verbatim in a pending clarification so
 * a resume can overlay just the one resolved field and re-run the same
 * per-intent handler (see PendingClarification).
 */
public record InterpretationParameters(
        String locationText,
        String specialtyText,
        ProviderReferenceValue providerReference,
        String roleText,
        ContactType contactType,
        TimeExpressionValue timeExpression,
        TimeContext timeContext,
        DeclaredUrgency declaredUrgency) {

    public static InterpretationParameters empty() {
        return new InterpretationParameters(null, null, null, null, null, null, null, null);
    }

    public InterpretationParameters withLocationText(String newLocationText) {
        return new InterpretationParameters(
                newLocationText, specialtyText, providerReference, roleText, contactType, timeExpression,
                timeContext, declaredUrgency);
    }

    public InterpretationParameters withSpecialtyText(String newSpecialtyText) {
        return new InterpretationParameters(
                locationText, newSpecialtyText, providerReference, roleText, contactType, timeExpression,
                timeContext, declaredUrgency);
    }

    public InterpretationParameters withRoleText(String newRoleText) {
        return new InterpretationParameters(
                locationText, specialtyText, providerReference, newRoleText, contactType, timeExpression,
                timeContext, declaredUrgency);
    }

    public InterpretationParameters withProviderReference(ProviderReferenceValue newProviderReference) {
        return new InterpretationParameters(
                locationText, specialtyText, newProviderReference, roleText, contactType, timeExpression,
                timeContext, declaredUrgency);
    }

    public InterpretationParameters withTimeExpression(TimeExpressionValue newTimeExpression) {
        return new InterpretationParameters(
                locationText, specialtyText, providerReference, roleText, contactType, newTimeExpression,
                timeContext, declaredUrgency);
    }

    public InterpretationParameters withDeclaredUrgency(DeclaredUrgency newDeclaredUrgency) {
        return new InterpretationParameters(
                locationText, specialtyText, providerReference, roleText, contactType, timeExpression,
                timeContext, newDeclaredUrgency);
    }
}
