"""Thin client for Ollama's local /api/generate endpoint.

Ollama serves Python only (CLAUDE.md Ownership); nothing else in this
service talks to it. `format` is passed a JSON schema so Ollama constrains
generation to that shape, but that is a best-effort generation aid, not a
trust boundary: the caller (interpretation/service.py) always re-validates
the returned JSON with Pydantic regardless (CLAUDE.md Trust Rules).
"""

from __future__ import annotations

import json

import httpx

from ai_service.config import settings


class OllamaUnavailableError(RuntimeError):
    """Raised on transport failure or timeout talking to Ollama."""


class OllamaMalformedResponseError(RuntimeError):
    """Raised when Ollama's response body is not valid JSON."""


def generate_json(*, prompt: str, json_schema: dict) -> dict:
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
    except httpx.HTTPError as exc:
        raise OllamaUnavailableError(f"Ollama request failed: {exc}") from exc

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
