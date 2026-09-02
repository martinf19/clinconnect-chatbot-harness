# ClinConnect Enterprise Chatbot Harness

## Local POC
The POC runs entirely on the developer machine without containers:
- React + TypeScript + Vite
- Spring Boot
- Python + FastAPI + Pydantic
- Ollama
- H2 embedded database
- Spring Boot in-memory conversation/session/idempotency state

No POC dependency on PostgreSQL, Redis, Docker, Docker Desktop, Colima, Podman, or Compose. Spring restart may clear active sessions.

## Future Production Target
The target production design remains React + Spring Boot + Python AI service, with PostgreSQL for relational persistence where appropriate and Redis for bounded conversation/session/idempotency state.

The POC must preserve interfaces so H2/in-memory implementations can be replaced without changing chatbot contracts.

## Core Execution
```text
user language -> Python interpretation -> Spring canonicalization/clarification -> Spring authorization/tool execution -> domain result -> Spring state update -> deterministic Spring response
```

Claude Code must follow `CLAUDE.md` and `planning/PLAN.md` one phase at a time.
