from fastapi.testclient import TestClient

import ai_service.main as main_module
from ai_service.interpretation.enums import InterpretationStatus
from ai_service.interpretation.schemas import InterpretResponse
from ai_service.interpretation.service import InterpretationFailedError
from ai_service.ollama_client import OllamaUnavailableError

client = TestClient(main_module.app)


def test_interpret_returns_200_with_valid_body(monkeypatch):
    def fake_interpret(request):
        return InterpretResponse(interpretation_status=InterpretationStatus.UNSUPPORTED)

    monkeypatch.setattr(main_module, "interpret", fake_interpret)

    response = client.post("/interpret", json={"message": "anything"})

    assert response.status_code == 200
    assert response.json()["interpretation_status"] == "UNSUPPORTED"


def test_interpret_returns_502_when_interpretation_fails(monkeypatch):
    def fake_interpret(request):
        raise InterpretationFailedError("model kept producing invalid output")

    monkeypatch.setattr(main_module, "interpret", fake_interpret)

    response = client.post("/interpret", json={"message": "anything"})

    assert response.status_code == 502


def test_interpret_returns_503_when_ollama_unavailable(monkeypatch):
    def fake_interpret(request):
        raise OllamaUnavailableError("connection refused")

    monkeypatch.setattr(main_module, "interpret", fake_interpret)

    response = client.post("/interpret", json={"message": "anything"})

    assert response.status_code == 503


def test_interpret_rejects_unknown_request_fields():
    response = client.post("/interpret", json={"message": "hi", "proposed_tool": "admin_dump"})

    assert response.status_code == 422


def test_interpret_requires_message():
    response = client.post("/interpret", json={})

    assert response.status_code == 422
