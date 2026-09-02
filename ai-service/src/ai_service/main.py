from fastapi import FastAPI, HTTPException

from ai_service.config import settings
from ai_service.interpretation.schemas import InterpretRequest, InterpretResponse
from ai_service.interpretation.service import InterpretationFailedError, interpret
from ai_service.ollama_client import OllamaUnavailableError

app = FastAPI(title="ClinConnect AI Interpretation Service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "app_env": settings.app_env}


@app.post("/interpret", response_model=InterpretResponse)
def interpret_message(request: InterpretRequest) -> InterpretResponse:
    """Language interpretation only (CLAUDE.md Ownership: Python AI Service).

    Never executes tools, never returns canonical entity IDs/tool IDs. A
    non-200 response means interpretation failed outright (fail closed,
    docs/09-SECURITY.md); it is not a legitimate chatbot answer.
    """
    try:
        return interpret(request)
    except InterpretationFailedError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except OllamaUnavailableError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
