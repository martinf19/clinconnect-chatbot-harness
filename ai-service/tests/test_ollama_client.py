import httpx
import pytest

from ai_service.config import settings
from ai_service.ollama_client import (
    OllamaMalformedResponseError,
    OllamaUnavailableError,
    generate_json,
)

_REQUEST = httpx.Request("POST", "http://localhost:11434/api/generate")


def _response(status_code: int, json_body) -> httpx.Response:
    return httpx.Response(status_code, json=json_body, request=_REQUEST)


@pytest.fixture(autouse=True)
def _no_retry_delay(monkeypatch):
    # Phase 7 retry policy sleeps between attempts in real usage; keep tests instant.
    monkeypatch.setattr(settings, "ollama_retry_delay_seconds", 0)


def test_generate_json_returns_parsed_response(monkeypatch):
    def fake_post(url, json, timeout):
        return _response(200, {"response": '{"interpretation_status": "UNSUPPORTED"}'})

    monkeypatch.setattr(httpx, "post", fake_post)

    result = generate_json(prompt="hi", json_schema={"type": "object"})

    assert result == {"interpretation_status": "UNSUPPORTED"}


def test_generate_json_retries_once_on_transient_transport_failure_then_succeeds(monkeypatch):
    attempts = {"count": 0}

    def fake_post(url, json, timeout):
        attempts["count"] += 1
        if attempts["count"] == 1:
            raise httpx.ConnectError("connection refused")
        return _response(200, {"response": '{"interpretation_status": "UNSUPPORTED"}'})

    monkeypatch.setattr(httpx, "post", fake_post)

    result = generate_json(prompt="hi", json_schema={"type": "object"})

    assert result == {"interpretation_status": "UNSUPPORTED"}
    assert attempts["count"] == 2


def test_generate_json_raises_on_transport_error_after_exhausting_retry(monkeypatch):
    attempts = {"count": 0}

    def fake_post(url, json, timeout):
        attempts["count"] += 1
        raise httpx.ConnectError("connection refused")

    monkeypatch.setattr(httpx, "post", fake_post)

    with pytest.raises(OllamaUnavailableError):
        generate_json(prompt="hi", json_schema={"type": "object"})
    # Exactly one retry — not zero (would mean no retry happened), not more (would mean
    # the "single bounded retry" policy was exceeded).
    assert attempts["count"] == 2


def test_generate_json_raises_on_timeout_after_exhausting_retry(monkeypatch):
    attempts = {"count": 0}

    def fake_post(url, json, timeout):
        attempts["count"] += 1
        raise httpx.TimeoutException("timed out")

    monkeypatch.setattr(httpx, "post", fake_post)

    with pytest.raises(OllamaUnavailableError):
        generate_json(prompt="hi", json_schema={"type": "object"})
    assert attempts["count"] == 2


def test_generate_json_raises_on_http_error_status_without_retrying(monkeypatch):
    attempts = {"count": 0}

    def fake_post(url, json, timeout):
        attempts["count"] += 1
        return _response(500, {"error": "boom"})

    monkeypatch.setattr(httpx, "post", fake_post)

    with pytest.raises(OllamaUnavailableError):
        generate_json(prompt="hi", json_schema={"type": "object"})
    # A well-formed error response is never retried — Ollama already answered.
    assert attempts["count"] == 1


def test_generate_json_raises_when_model_output_is_not_json(monkeypatch):
    def fake_post(url, json, timeout):
        return _response(200, {"response": "{not-valid-json"})

    monkeypatch.setattr(httpx, "post", fake_post)

    with pytest.raises(OllamaMalformedResponseError):
        generate_json(prompt="hi", json_schema={"type": "object"})


def test_generate_json_raises_when_envelope_missing_response_field(monkeypatch):
    def fake_post(url, json, timeout):
        return _response(200, {"unexpected": "shape"})

    monkeypatch.setattr(httpx, "post", fake_post)

    with pytest.raises(OllamaMalformedResponseError):
        generate_json(prompt="hi", json_schema={"type": "object"})
