import httpx
import pytest

from ai_service.ollama_client import (
    OllamaMalformedResponseError,
    OllamaUnavailableError,
    generate_json,
)

_REQUEST = httpx.Request("POST", "http://localhost:11434/api/generate")


def _response(status_code: int, json_body) -> httpx.Response:
    return httpx.Response(status_code, json=json_body, request=_REQUEST)


def test_generate_json_returns_parsed_response(monkeypatch):
    def fake_post(url, json, timeout):
        return _response(200, {"response": '{"interpretation_status": "UNSUPPORTED"}'})

    monkeypatch.setattr(httpx, "post", fake_post)

    result = generate_json(prompt="hi", json_schema={"type": "object"})

    assert result == {"interpretation_status": "UNSUPPORTED"}


def test_generate_json_raises_on_transport_error(monkeypatch):
    def fake_post(url, json, timeout):
        raise httpx.ConnectError("connection refused")

    monkeypatch.setattr(httpx, "post", fake_post)

    with pytest.raises(OllamaUnavailableError):
        generate_json(prompt="hi", json_schema={"type": "object"})


def test_generate_json_raises_on_timeout(monkeypatch):
    def fake_post(url, json, timeout):
        raise httpx.TimeoutException("timed out")

    monkeypatch.setattr(httpx, "post", fake_post)

    with pytest.raises(OllamaUnavailableError):
        generate_json(prompt="hi", json_schema={"type": "object"})


def test_generate_json_raises_on_http_error_status(monkeypatch):
    def fake_post(url, json, timeout):
        return _response(500, {"error": "boom"})

    monkeypatch.setattr(httpx, "post", fake_post)

    with pytest.raises(OllamaUnavailableError):
        generate_json(prompt="hi", json_schema={"type": "object"})


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
