from fastapi.testclient import TestClient

from ai_service.main import app

client = TestClient(app)


def test_health_returns_up():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
