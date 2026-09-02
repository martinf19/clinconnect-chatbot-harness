#!/usr/bin/env python3
"""Runs tests/evaluation/intent-evaluation.yaml (docs/10-EVALUATION-PLAN.md
Layer A) against the live model configured by OLLAMA_MODEL.

This is a standalone script, not a pytest case: Layer A calls a live local
LLM, so it is inherently non-deterministic and slow (docs/10 "Interpretation
accuracy percentage may be reported, but no business threshold is
invented"). planning/DEFINITION-OF-DONE.md's "100% passing" bar applies to
deterministic/unit/contract tests; this script reports accuracy for human
review instead of gating CI.

Usage:
    ai-service/.venv/bin/python ai-service/scripts/run_intent_evaluation.py

Rerun this whenever OLLAMA_MODEL changes (docs/10 "Model Changes") and
record the model name/tag and pass count alongside the change.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))

import yaml  # noqa: E402

from ai_service.config import settings  # noqa: E402
from ai_service.interpretation.schemas import (  # noqa: E402
    InterpretRequest,
    LastResultContextInput,
    SessionContextInput,
)
from ai_service.interpretation.service import InterpretationFailedError, interpret  # noqa: E402
from ai_service.ollama_client import OllamaUnavailableError  # noqa: E402

EVALUATION_FILE = Path(__file__).resolve().parent.parent.parent / "tests/evaluation/intent-evaluation.yaml"


def _build_request(case_input: dict) -> InterpretRequest:
    session_context = None
    raw_context = case_input.get("session_context")
    if raw_context:
        raw_last_result = raw_context.get("last_result") or {}
        session_context = SessionContextInput(
            last_result=LastResultContextInput(
                has_single_provider=raw_last_result.get("has_single_provider", False),
                provider_display_name=raw_last_result.get("provider_display_name"),
            )
        )
    return InterpretRequest(message=case_input["message"], session_context=session_context)


def _mismatches(expected: dict, response) -> list[str]:
    problems: list[str] = []

    if "interpretation_status" in expected:
        actual = response.interpretation_status.value
        if actual != expected["interpretation_status"]:
            problems.append(f"interpretation_status: expected {expected['interpretation_status']}, got {actual}")

    if "intent_id" in expected:
        actual_intent = response.intent_id.value if response.intent_id else None
        if actual_intent != expected["intent_id"]:
            problems.append(f"intent_id: expected {expected['intent_id']}, got {actual_intent}")

    expected_parameters = expected.get("parameters") or {}
    actual_parameters = response.parameters.model_dump(exclude_none=True, mode="json")
    for key, expected_value in expected_parameters.items():
        actual_value = actual_parameters.get(key)
        if isinstance(expected_value, dict):
            for sub_key, sub_expected in expected_value.items():
                sub_actual = (actual_value or {}).get(sub_key)
                if sub_actual != sub_expected:
                    problems.append(f"parameters.{key}.{sub_key}: expected {sub_expected}, got {sub_actual}")
        elif actual_value != expected_value:
            problems.append(f"parameters.{key}: expected {expected_value}, got {actual_value}")

    if "missing_parameters" in expected:
        expected_missing = set(expected["missing_parameters"])
        actual_missing = {p.value for p in response.missing_parameters}
        if expected_missing != actual_missing:
            problems.append(f"missing_parameters: expected {expected_missing}, got {actual_missing}")

    return problems


def main() -> int:
    doc = yaml.safe_load(EVALUATION_FILE.read_text(encoding="utf-8"))
    cases = doc["cases"]

    print(f"Model: {settings.ollama_model}  ({len(cases)} cases from {EVALUATION_FILE.name})\n")

    passed = 0
    for case in cases:
        case_id = case["id"]
        expected = case["expected"]
        try:
            request = _build_request(case["input"])
            response = interpret(request)
            problems = _mismatches(expected, response)
        except (InterpretationFailedError, OllamaUnavailableError) as exc:
            problems = [f"interpretation failed: {exc}"]

        if problems:
            print(f"FAIL {case_id}")
            for problem in problems:
                print(f"     {problem}")
        else:
            passed += 1
            print(f"PASS {case_id}")

    total = len(cases)
    print(f"\n{passed}/{total} passed ({passed / total:.0%})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
