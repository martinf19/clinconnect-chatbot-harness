"""Flat schemas the model is actually asked to fill in.

These are deliberately flat (no nested objects, no cross-field rules) so the
JSON schema handed to Ollama's structured-output grammar stays simple and
reliable. They carry no validation of their own: mapping.py builds the
strict nested docs/05 contract (schemas.py) from these values, which is
where real enforcement happens. Nothing here is trusted until that mapping
succeeds (CLAUDE.md Trust Rules: model entity/text output is never trusted
directly).
"""

from __future__ import annotations

from datetime import date

from pydantic import BaseModel, ConfigDict

from ai_service.intents_config import IntentId
from ai_service.interpretation.enums import (
    ClarificationAnswerKind,
    ContactType,
    DeclaredUrgency,
    InterpretationStatus,
    ProviderReferenceKind,
    TimeContext,
    TimeExpressionKind,
)


class LlmModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class LlmIntentOutput(LlmModel):
    interpretation_status: InterpretationStatus
    intent_id: IntentId | None = None
    location_text: str | None = None
    specialty_text: str | None = None
    role_text: str | None = None
    contact_type: ContactType | None = None
    provider_reference_kind: ProviderReferenceKind | None = None
    provider_reference_text: str | None = None
    time_expression_kind: TimeExpressionKind | None = None
    time_expression_specific_date: date | None = None
    time_context: TimeContext | None = None
    declared_urgency: DeclaredUrgency | None = None


class LlmClarificationAnswerOutput(LlmModel):
    answer_kind: ClarificationAnswerKind
    selected_option_id: str | None = None
    value_text: str | None = None

    # Populated only when answer_kind is UNRELATED: the new message is
    # interpreted as a fresh request in the same call (config/prompts/
    # intent-router.md "If clearly unrelated, return UNRELATED").
    interpretation_status: InterpretationStatus | None = None
    intent_id: IntentId | None = None
    location_text: str | None = None
    specialty_text: str | None = None
    role_text: str | None = None
    contact_type: ContactType | None = None
    provider_reference_kind: ProviderReferenceKind | None = None
    provider_reference_text: str | None = None
    time_expression_kind: TimeExpressionKind | None = None
    time_expression_specific_date: date | None = None
    time_context: TimeContext | None = None
    declared_urgency: DeclaredUrgency | None = None
