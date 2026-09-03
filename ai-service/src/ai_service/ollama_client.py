"""Thin client for Ollama's local /api/generate endpoint.

Ollama serves Python only (CLAUDE.md Ownership); nothing else in this
service talks to it. `format` is passed a JSON schema so Ollama constrains
generation to that shape, but that is a best-effort generation aid, not a
trust boundary: the caller (interpretation/service.py) always re-validates
the returned JSON with Pydantic regardless (CLAUDE.md Trust Rules).
"""

from __future__ import annotations

import json
import time

import httpx

from ai_service.config import settings

_MAX_ATTEMPTS = 2


class OllamaUnavailableError(RuntimeError):
    """Raised on transport failure or timeout talking to Ollama."""


class OllamaMalformedResponseError(RuntimeError):
    """Raised when Ollama's response body is not valid JSON."""


def _post_with_retry(*, prompt: str, json_schema: dict) -> httpx.Response:
    """Phase 7 POC hardening: a single bounded retry, after a short delay, on a
    transport-level failure only (httpx.RequestError — connection refused/reset,
    timeout). A well-formed non-2xx response (httpx.HTTPStatusError, raised by
    raise_for_status()) is not retried: Ollama already responded, so retrying
    would just repeat the same structural failure.
    """
    last_error: httpx.RequestError | None = None
    for attempt in range(1, _MAX_ATTEMPTS + 1):
        try:
            response = httpx.post(
                f"{settings.ollama_base_url}/api/generate",
                json={
                    "model": settings.ollama_model,
                    "prompt": prompt,
                    "format": json_schema,
                    "stream": False,
                },
                timeout=settings.ollama_timeout_seconds,
            )
            response.raise_for_status()
            return response
        except httpx.HTTPStatusError as exc:
            raise OllamaUnavailableError(f"Ollama request failed: {exc}") from exc
        except httpx.RequestError as exc:
            last_error = exc
            if attempt < _MAX_ATTEMPTS:
                time.sleep(settings.ollama_retry_delay_seconds)

    raise OllamaUnavailableError(f"Ollama request failed after retry: {last_error}") from last_error


def generate_json(*, prompt: str, json_schema: dict) -> dict:
    response = _post_with_retry(prompt=prompt, json_schema=json_schema)

    try:
        envelope = response.json()
    except ValueError as exc:
        raise OllamaMalformedResponseError(f"Ollama response was not valid JSON: {exc}") from exc

    raw_output = envelope.get("response")
    if not isinstance(raw_output, str):
        raise OllamaMalformedResponseError("Ollama response envelope missing string 'response' field")

    try:
        return json.loads(raw_output)
    except ValueError as exc:
        raise OllamaMalformedResponseError(f"Model output was not valid JSON: {exc}") from exc
