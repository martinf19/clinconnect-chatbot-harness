"""Resolves configured file paths relative to the ai-service package root.

Mirrors the backend convention (backend/src/main/resources/application.yml
`clinconnect.config.intents-path: ../config/intents.yaml`): a relative path
is resolved against the directory each service runs from, so both services
can point at the same top-level config/ directory with the same default.
"""

from __future__ import annotations

from pathlib import Path

_AI_SERVICE_ROOT = Path(__file__).resolve().parent.parent.parent


def resolve_path(configured_path: str) -> Path:
    path = Path(configured_path)
    if not path.is_absolute():
        path = _AI_SERVICE_ROOT / configured_path
    return path
