"""Maps flat LLM output (llm_schemas.py) onto the strict public contract
(schemas.py).

`pydantic.ValidationError`/`ValueError` raised while building the strict
objects here is the enforcement point: it means the model produced
something that does not fit the docs/05 canonical parameter vocabulary
(e.g. a specific_date without SPECIFIC_DATE kind), and the caller
(service.py) treats that exactly like malformed JSON — retry, then fail
closed.

`missing_parameters` is deliberately not asked of the model at all: whether
a parameter is "always required by the chosen intent" is exactly the kind
of deterministic, config-driven fact config/intents.yaml already encodes
(intents_config.py), and a small local model reasoning about required-ness
on top of extraction proved unreliable in practice. Computing it here from
IntentCatalog is both simpler and more trustworthy, and it automatically
respects intent-router.md's "Do not treat location as automatically
missing for intents configured with BACKEND_UNIQUE_OR_CLARIFY" rule, since
those parameters are marked optional (not required) in config/intents.yaml
to begin with.
"""

from __future__ import annotations

from ai_service.interpretation.enums import (
    ClarificationAnswerKind,
    ClarificationParameterName,
    InterpretationStatus,
    ProviderReferenceKind,
    TimeExpressionKind,
)
from ai_service.interpretation.llm_schemas import LlmClarificationAnswerOutput, LlmIntentOutput
from ai_service.interpretation.schemas import (
    ClarificationAnswerResult,
    InterpretationParameters,
    InterpretResponse,
    ProviderReference,
    TimeExpression,
)
from ai_service.intents_config import intent_catalog


def _build_time_expression(kind: TimeExpressionKind | None, specific_date) -> TimeExpression | None:
    if kind is None:
        return None
    return TimeExpression(kind=kind, specific_date=specific_date)


def _build_provider_reference(kind: ProviderReferenceKind | None, text: str | None) -> ProviderReference | None:
    if kind is None:
        return None
    return ProviderReference(kind=kind, text=text)


def _build_parameters(
    *,
    location_text,
    specialty_text,
    role_text,
    contact_type,
    provider_reference_kind,
    provider_reference_text,
    time_expression_kind,
    time_expression_specific_date,
    time_context,
    declared_urgency,
) -> InterpretationParameters:
    return InterpretationParameters(
        location_text=location_text,
        specialty_text=specialty_text,
        role_text=role_text,
        contact_type=contact_type,
        provider_reference=_build_provider_reference(provider_reference_kind, provider_reference_text),
        time_expression=_build_time_expression(time_expression_kind, time_expression_specific_date),
        time_context=time_context,
        declared_urgency=declared_urgency,
    )


def _compute_missing_parameters(
    intent_id: str | None, parameters: InterpretationParameters
) -> list[ClarificationParameterName]:
    if intent_id is None:
        return []
    definition = intent_catalog().by_id(intent_id)
    if definition is None:
        return []
    return [
        ClarificationParameterName(name)
        for name in definition.required_parameters
        if getattr(parameters, name, None) is None
    ]


def map_intent_output(llm: LlmIntentOutput) -> InterpretResponse:
    parameters = _build_parameters(
        location_text=llm.location_text,
        specialty_text=llm.specialty_text,
        role_text=llm.role_text,
        contact_type=llm.contact_type,
        provider_reference_kind=llm.provider_reference_kind,
        provider_reference_text=llm.provider_reference_text,
        time_expression_kind=llm.time_expression_kind,
        time_expression_specific_date=llm.time_expression_specific_date,
        time_context=llm.time_context,
        declared_urgency=llm.declared_urgency,
    )
    intent_id = llm.intent_id if llm.interpretation_status == InterpretationStatus.INTERPRETED else None
    missing_parameters = (
        _compute_missing_parameters(llm.intent_id, parameters)
        if llm.interpretation_status == InterpretationStatus.INTERPRETED
        else []
    )
    return InterpretResponse(
        interpretation_status=llm.interpretation_status,
        intent_id=intent_id,
        parameters=parameters,
        missing_parameters=missing_parameters,
    )


def map_clarification_answer_output(
    llm: LlmClarificationAnswerOutput, *, valid_option_ids: frozenset[str] | None = None
) -> InterpretResponse:
    if (
        llm.answer_kind == ClarificationAnswerKind.SELECTED_OPTION
        and valid_option_ids is not None
        and llm.selected_option_id not in valid_option_ids
    ):
        # intent-router.md: "Do not invent option IDs." An option ID that
        # was not actually offered is treated as invalid model output, not
        # trusted downstream (CLAUDE.md Trust Rules).
        raise ValueError(
            f"Model selected option_id '{llm.selected_option_id}' which was not among the "
            "options supplied in the pending clarification"
        )

    clarification_answer = ClarificationAnswerResult(
        answer_kind=llm.answer_kind,
        selected_option_id=llm.selected_option_id,
        value_text=llm.value_text,
    )

    if llm.answer_kind == ClarificationAnswerKind.UNRELATED:
        if llm.interpretation_status is None:
            raise ValueError("interpretation_status is required when answer_kind is UNRELATED")
        parameters = _build_parameters(
            location_text=llm.location_text,
            specialty_text=llm.specialty_text,
            role_text=llm.role_text,
            contact_type=llm.contact_type,
            provider_reference_kind=llm.provider_reference_kind,
            provider_reference_text=llm.provider_reference_text,
            time_expression_kind=llm.time_expression_kind,
            time_expression_specific_date=llm.time_expression_specific_date,
            time_context=llm.time_context,
            declared_urgency=llm.declared_urgency,
        )
        intent_id = llm.intent_id if llm.interpretation_status == InterpretationStatus.INTERPRETED else None
        missing_parameters = (
            _compute_missing_parameters(llm.intent_id, parameters)
            if llm.interpretation_status == InterpretationStatus.INTERPRETED
            else []
        )
        return InterpretResponse(
            interpretation_status=llm.interpretation_status,
            intent_id=intent_id,
            parameters=parameters,
            missing_parameters=missing_parameters,
            clarification_answer=clarification_answer,
        )

    status = (
        InterpretationStatus.NEEDS_LANGUAGE_CLARIFICATION
        if llm.answer_kind == ClarificationAnswerKind.UNRESOLVED
        else InterpretationStatus.INTERPRETED
    )
    return InterpretResponse(
        interpretation_status=status,
        intent_id=None,
        clarification_answer=clarification_answer,
    )
