"""The strict interpretation contract exposed by POST /interpret.

Every model forbids unknown fields and enforces enums (NFR-005, docs/09
"AI Output Security"). This is the contract Spring will call in Phase 3; it
never contains canonical entity IDs, tool IDs, or executable timestamps
(docs/05-INTENT-CATALOG.md "Canonical entity IDs are never returned by the
model").
"""

from __future__ import annotations

from datetime import date

from pydantic import BaseModel, ConfigDict, model_validator

from ai_service.intents_config import IntentId
from ai_service.interpretation.enums import (
    ClarificationAnswerKind,
    ClarificationParameterName,
    ContactType,
    DeclaredUrgency,
    InterpretationStatus,
    ProviderReferenceKind,
    TimeContext,
    TimeExpressionKind,
)


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class TimeExpression(StrictModel):
    kind: TimeExpressionKind
    specific_date: date | None = None

    @model_validator(mode="after")
    def _validate_specific_date(self) -> "TimeExpression":
        if self.kind == TimeExpressionKind.SPECIFIC_DATE and self.specific_date is None:
            raise ValueError("specific_date is required when kind is SPECIFIC_DATE")
        if self.kind != TimeExpressionKind.SPECIFIC_DATE and self.specific_date is not None:
            raise ValueError("specific_date is only allowed when kind is SPECIFIC_DATE")
        return self


class ProviderReference(StrictModel):
    kind: ProviderReferenceKind
    text: str | None = None

    @model_validator(mode="after")
    def _validate_text(self) -> "ProviderReference":
        if self.kind == ProviderReferenceKind.EXPLICIT_TEXT and not self.text:
            raise ValueError("text is required when kind is EXPLICIT_TEXT")
        if self.kind == ProviderReferenceKind.LAST_RESULT_PROVIDER and self.text is not None:
            raise ValueError("text is not allowed when kind is LAST_RESULT_PROVIDER")
        return self


class InterpretationParameters(StrictModel):
    """The canonical parameter vocabulary (docs/05-INTENT-CATALOG.md).

    All raw text; Spring owns canonicalization/aliasing (FR-015).
    """

    location_text: str | None = None
    specialty_text: str | None = None
    provider_reference: ProviderReference | None = None
    role_text: str | None = None
    contact_type: ContactType | None = None
    time_expression: TimeExpression | None = None
    time_context: TimeContext | None = None
    declared_urgency: DeclaredUrgency | None = None


class LastResultContextInput(StrictModel):
    """Safe, non-sensitive session context Spring may supply (FR-013).

    Only enough to let language interpretation recognize a pronoun
    reference; Spring still owns resolving it to a canonical provider ID.
    """

    has_single_provider: bool = False
    provider_display_name: str | None = None


class SessionContextInput(StrictModel):
    last_result: LastResultContextInput | None = None


class ClarificationOption(StrictModel):
    option_id: str
    label: str


class PendingClarificationInput(StrictModel):
    reason: str
    parameter: ClarificationParameterName
    options: list[ClarificationOption] = []


class InterpretRequest(StrictModel):
    message: str
    session_context: SessionContextInput | None = None
    pending_clarification: PendingClarificationInput | None = None


class ClarificationAnswerResult(StrictModel):
    answer_kind: ClarificationAnswerKind
    selected_option_id: str | None = None
    value_text: str | None = None

    @model_validator(mode="after")
    def _validate_fields_for_kind(self) -> "ClarificationAnswerResult":
        if self.answer_kind == ClarificationAnswerKind.SELECTED_OPTION and not self.selected_option_id:
            raise ValueError("selected_option_id is required when answer_kind is SELECTED_OPTION")
        if self.answer_kind != ClarificationAnswerKind.SELECTED_OPTION and self.selected_option_id is not None:
            raise ValueError("selected_option_id is only allowed when answer_kind is SELECTED_OPTION")
        if self.answer_kind == ClarificationAnswerKind.VALUE_PROVIDED and not self.value_text:
            raise ValueError("value_text is required when answer_kind is VALUE_PROVIDED")
        if self.answer_kind != ClarificationAnswerKind.VALUE_PROVIDED and self.value_text is not None:
            raise ValueError("value_text is only allowed when answer_kind is VALUE_PROVIDED")
        return self


class InterpretResponse(StrictModel):
    interpretation_status: InterpretationStatus
    intent_id: IntentId | None = None
    parameters: InterpretationParameters = InterpretationParameters()
    missing_parameters: list[ClarificationParameterName] = []
    clarification_answer: ClarificationAnswerResult | None = None

    @model_validator(mode="after")
    def _validate_intent_id_presence(self) -> "InterpretResponse":
        if self.interpretation_status == InterpretationStatus.INTERPRETED and self.intent_id is None:
            if self.clarification_answer is None:
                raise ValueError("intent_id is required when interpretation_status is INTERPRETED")
        if self.interpretation_status != InterpretationStatus.INTERPRETED and self.intent_id is not None:
            raise ValueError("intent_id is only allowed when interpretation_status is INTERPRETED")
        return self
