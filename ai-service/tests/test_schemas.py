import pytest
from pydantic import ValidationError

from ai_service.interpretation.enums import (
    ClarificationAnswerKind,
    ClarificationParameterName,
    InterpretationStatus,
    ProviderReferenceKind,
    TimeExpressionKind,
)
from ai_service.interpretation.schemas import (
    ClarificationAnswerResult,
    InterpretRequest,
    InterpretResponse,
    ProviderReference,
    TimeExpression,
)


def test_time_expression_requires_specific_date_for_specific_date_kind():
    with pytest.raises(ValidationError):
        TimeExpression(kind=TimeExpressionKind.SPECIFIC_DATE)


def test_time_expression_forbids_specific_date_for_other_kinds():
    with pytest.raises(ValidationError):
        TimeExpression(kind=TimeExpressionKind.CURRENT, specific_date="2026-09-03")


def test_time_expression_valid_specific_date():
    expr = TimeExpression(kind=TimeExpressionKind.SPECIFIC_DATE, specific_date="2026-09-03")
    assert expr.kind == TimeExpressionKind.SPECIFIC_DATE


def test_provider_reference_requires_text_for_explicit():
    with pytest.raises(ValidationError):
        ProviderReference(kind=ProviderReferenceKind.EXPLICIT_TEXT)


def test_provider_reference_forbids_text_for_last_result():
    with pytest.raises(ValidationError):
        ProviderReference(kind=ProviderReferenceKind.LAST_RESULT_PROVIDER, text="Dr. Avery Chen")


def test_provider_reference_last_result_without_text_is_valid():
    ref = ProviderReference(kind=ProviderReferenceKind.LAST_RESULT_PROVIDER)
    assert ref.text is None


def test_interpret_response_requires_intent_id_when_interpreted():
    with pytest.raises(ValidationError):
        InterpretResponse(interpretation_status=InterpretationStatus.INTERPRETED)


def test_interpret_response_forbids_intent_id_when_unsupported():
    with pytest.raises(ValidationError):
        InterpretResponse(
            interpretation_status=InterpretationStatus.UNSUPPORTED,
            intent_id="get_locations",
        )


def test_interpret_response_allows_missing_intent_id_with_clarification_answer():
    response = InterpretResponse(
        interpretation_status=InterpretationStatus.INTERPRETED,
        clarification_answer=ClarificationAnswerResult(
            answer_kind=ClarificationAnswerKind.CANCEL,
        ),
    )
    assert response.intent_id is None


def test_clarification_answer_requires_selected_option_id():
    with pytest.raises(ValidationError):
        ClarificationAnswerResult(answer_kind=ClarificationAnswerKind.SELECTED_OPTION)


def test_clarification_answer_requires_value_text():
    with pytest.raises(ValidationError):
        ClarificationAnswerResult(answer_kind=ClarificationAnswerKind.VALUE_PROVIDED)


def test_clarification_answer_cancel_forbids_extra_fields():
    with pytest.raises(ValidationError):
        ClarificationAnswerResult(
            answer_kind=ClarificationAnswerKind.CANCEL, selected_option_id="loc-oakland"
        )


def test_interpret_request_forbids_unknown_fields():
    with pytest.raises(ValidationError):
        InterpretRequest(message="hi", proposed_tool="admin_dump")


def test_interpret_response_rejects_unknown_intent_id():
    with pytest.raises(ValidationError):
        InterpretResponse(interpretation_status=InterpretationStatus.INTERPRETED, intent_id="admin_dump")


def test_interpret_response_rejects_illegal_time_expression_kind():
    with pytest.raises(ValidationError):
        InterpretResponse(
            interpretation_status=InterpretationStatus.INTERPRETED,
            intent_id="get_oncall_now",
            parameters={"time_expression": {"kind": "YESTERDAY_FOREVER"}},
        )


def test_missing_parameters_rejects_unknown_parameter_name():
    with pytest.raises(ValidationError):
        InterpretResponse(
            interpretation_status=InterpretationStatus.INTERPRETED,
            intent_id="get_locations",
            missing_parameters=[ClarificationParameterName.LOCATION_TEXT, "not_a_real_parameter"],
        )
