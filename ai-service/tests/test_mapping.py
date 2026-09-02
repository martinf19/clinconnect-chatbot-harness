import pytest
from pydantic import ValidationError

from ai_service.interpretation.enums import (
    ClarificationAnswerKind,
    InterpretationStatus,
    ProviderReferenceKind,
    TimeExpressionKind,
)
from ai_service.interpretation.llm_schemas import LlmClarificationAnswerOutput, LlmIntentOutput
from ai_service.interpretation.mapping import map_clarification_answer_output, map_intent_output


def test_map_intent_output_basic():
    llm = LlmIntentOutput(
        interpretation_status=InterpretationStatus.INTERPRETED,
        intent_id="get_oncall_now",
        specialty_text="Cardiology",
        time_expression_kind=TimeExpressionKind.CURRENT,
    )

    response = map_intent_output(llm)

    assert response.intent_id == "get_oncall_now"
    assert response.parameters.specialty_text == "Cardiology"
    assert response.parameters.time_expression.kind == TimeExpressionKind.CURRENT


def test_map_intent_output_builds_provider_reference():
    llm = LlmIntentOutput(
        interpretation_status=InterpretationStatus.INTERPRETED,
        intent_id="get_contact_info",
        provider_reference_kind=ProviderReferenceKind.EXPLICIT_TEXT,
        provider_reference_text="Dr. Avery Chen",
    )

    response = map_intent_output(llm)

    assert response.parameters.provider_reference.kind == ProviderReferenceKind.EXPLICIT_TEXT
    assert response.parameters.provider_reference.text == "Dr. Avery Chen"


def test_map_intent_output_rejects_specific_date_without_kind_mismatch():
    llm = LlmIntentOutput(
        interpretation_status=InterpretationStatus.INTERPRETED,
        intent_id="get_oncall_schedule",
        time_expression_kind=TimeExpressionKind.CURRENT,
        time_expression_specific_date="2026-09-03",
    )

    with pytest.raises(ValidationError):
        map_intent_output(llm)


def test_map_intent_output_computes_missing_required_parameter():
    llm = LlmIntentOutput(interpretation_status=InterpretationStatus.INTERPRETED, intent_id="get_specialties")

    response = map_intent_output(llm)

    assert response.parameters.location_text is None
    assert response.missing_parameters == ["location_text"]


def test_map_intent_output_does_not_treat_backend_unique_or_clarify_location_as_missing():
    llm = LlmIntentOutput(
        interpretation_status=InterpretationStatus.INTERPRETED,
        intent_id="get_oncall_now",
        specialty_text="Cardiology",
        time_expression_kind=TimeExpressionKind.CURRENT,
    )

    response = map_intent_output(llm)

    assert response.missing_parameters == []


def test_map_intent_output_drops_stray_intent_id_when_not_interpreted():
    # A model emitting UNSUPPORTED alongside a guessed intent_id is a benign
    # inconsistency, not unsafe: no tool can execute from an UNSUPPORTED
    # result either way, so this is normalized rather than treated as
    # invalid output requiring a retry.
    llm = LlmIntentOutput(
        interpretation_status=InterpretationStatus.UNSUPPORTED,
        intent_id="get_oncall_now",
    )

    response = map_intent_output(llm)

    assert response.interpretation_status == InterpretationStatus.UNSUPPORTED
    assert response.intent_id is None


def test_map_clarification_answer_selected_option():
    llm = LlmClarificationAnswerOutput(
        answer_kind=ClarificationAnswerKind.SELECTED_OPTION,
        selected_option_id="loc-oakland",
    )

    response = map_clarification_answer_output(llm)

    assert response.interpretation_status == InterpretationStatus.INTERPRETED
    assert response.intent_id is None
    assert response.clarification_answer.selected_option_id == "loc-oakland"


def test_map_clarification_answer_unresolved_maps_to_needs_language_clarification():
    llm = LlmClarificationAnswerOutput(answer_kind=ClarificationAnswerKind.UNRESOLVED)

    response = map_clarification_answer_output(llm)

    assert response.interpretation_status == InterpretationStatus.NEEDS_LANGUAGE_CLARIFICATION
    assert response.clarification_answer.answer_kind == ClarificationAnswerKind.UNRESOLVED


def test_map_clarification_answer_unrelated_requires_interpretation_status():
    llm = LlmClarificationAnswerOutput(answer_kind=ClarificationAnswerKind.UNRELATED)

    with pytest.raises(ValueError):
        map_clarification_answer_output(llm)


def test_map_clarification_answer_unrelated_carries_new_intent():
    llm = LlmClarificationAnswerOutput(
        answer_kind=ClarificationAnswerKind.UNRELATED,
        interpretation_status=InterpretationStatus.INTERPRETED,
        intent_id="get_locations",
    )

    response = map_clarification_answer_output(llm)

    assert response.intent_id == "get_locations"
    assert response.clarification_answer.answer_kind == ClarificationAnswerKind.UNRELATED
