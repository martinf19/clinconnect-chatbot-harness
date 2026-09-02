"""Orchestrates one /interpret call: prompt -> Ollama -> validate -> map.

Retries only cover invalid/malformed structured output from the model
(AI_MAX_RETRIES_FOR_INVALID_STRUCTURED_OUTPUT), not Ollama being unreachable
— outage/retry policy for that is explicit P1 hardening backlog
(planning/BACKLOG.md), out of Phase 2 scope. A transport failure propagates
immediately as OllamaUnavailableError.

Exhausting retries on invalid output raises InterpretationFailedError. The
caller (the /interpret route) turns that into a non-200 response: docs/09
"malformed AI output fails closed" — this service never silently downgrades
a failed interpretation into a fabricated UNSUPPORTED result.
"""

from __future__ import annotations

from collections.abc import Callable
from functools import partial

from pydantic import ValidationError

from ai_service.config import settings
from ai_service.interpretation.llm_schemas import LlmClarificationAnswerOutput, LlmIntentOutput
from ai_service.interpretation.mapping import map_clarification_answer_output, map_intent_output
from ai_service.interpretation.prompt_builder import (
    build_clarification_answer_prompt,
    build_intent_interpretation_prompt,
)
from ai_service.interpretation.schemas import InterpretRequest, InterpretResponse
from ai_service.ollama_client import OllamaMalformedResponseError, generate_json

GenerateFn = Callable[..., dict]


class InterpretationFailedError(RuntimeError):
    """Raised when the model could not produce valid structured output
    within the configured retry budget."""


def _generate_validate_and_map(
    *,
    prompt: str,
    generate_fn: GenerateFn,
    llm_model_cls: type,
    map_fn: Callable[..., InterpretResponse],
) -> InterpretResponse:
    max_attempts = 1 + max(settings.ai_max_retries_for_invalid_structured_output, 0)
    last_error: Exception | None = None

    for _attempt in range(max_attempts):
        try:
            raw = generate_fn(prompt=prompt, json_schema=llm_model_cls.model_json_schema())
            llm_output = llm_model_cls.model_validate(raw)
            return map_fn(llm_output)
        except (OllamaMalformedResponseError, ValidationError, ValueError) as exc:
            last_error = exc
            continue

    raise InterpretationFailedError(
        f"Model produced invalid structured output after {max_attempts} attempt(s): {last_error}"
    ) from last_error


def interpret(request: InterpretRequest, *, generate_fn: GenerateFn = generate_json) -> InterpretResponse:
    if request.pending_clarification is not None:
        valid_option_ids = frozenset(
            option.option_id for option in request.pending_clarification.options
        )
        return _generate_validate_and_map(
            prompt=build_clarification_answer_prompt(request),
            generate_fn=generate_fn,
            llm_model_cls=LlmClarificationAnswerOutput,
            map_fn=partial(map_clarification_answer_output, valid_option_ids=valid_option_ids),
        )

    return _generate_validate_and_map(
        prompt=build_intent_interpretation_prompt(request),
        generate_fn=generate_fn,
        llm_model_cls=LlmIntentOutput,
        map_fn=map_intent_output,
    )
