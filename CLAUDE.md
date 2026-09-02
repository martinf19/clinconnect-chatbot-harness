# Claude Code Operating Contract

## Purpose
This repository is the implementation harness for the ClinConnect enterprise chatbot POC. Treat it as the implementation contract; do not invent behavior at undocumented seams.

## Mandatory Read Order
Read `README.md`, all numbered `docs/`, `planning/PLAN.md`, `planning/BACKLOG.md`, `planning/DEFINITION-OF-DONE.md`, `config/intents.yaml`, `config/tools.yaml`, all prompts, and all evaluation files before implementation.

## Phase Discipline
Implement one phase from `planning/PLAN.md` at a time and stop at each phase boundary. Report conflicts instead of improvising.

## Target Production Architecture
Do not redesign the production target:
- React + TypeScript + Vite
- Spring Boot trusted backend
- Python + FastAPI + Pydantic AI service
- approved model endpoint
- PostgreSQL for relational persistence where appropriate
- Redis for bounded conversation/session/idempotency state

## Local POC Runtime
For the POC only:
- React + TypeScript + Vite
- Spring Boot
- Python + FastAPI + Pydantic
- Ollama
- H2 embedded database for synthetic domain data
- Spring Boot in-memory conversation/session/idempotency state

The POC must not require PostgreSQL, Redis, Docker, Docker Desktop, Colima, Podman, Compose, or any container runtime.

Spring restart may discard active POC session/conversation/idempotency state. This is accepted POC behavior.

## Replacement Seams
Preserve clean Spring-owned abstractions so future production can replace:
- H2 -> PostgreSQL or approved source adapter
- Spring in-memory conversation state -> Redis

without changing frontend API, AI API, intent IDs, clarification semantics, tool semantics, or session semantics.

## Ownership
### React
Owns UI/presentation state and calls Spring only.

### Spring Boot
Owns development auth stubs for the POC and the future production security boundary, plus session ownership, idempotency, correlation IDs, canonicalization, time normalization, ambiguity/clarification, deterministic intent-to-tool mapping, business logic, persistence/state abstractions, tool execution, and factual response formatting.

POC implementations:
- H2 domain repository implementation
- Spring in-memory conversation-state implementation

Future production implementations:
- PostgreSQL/source adapter
- Redis state implementation

### Python AI Service
Owns language interpretation only. It never owns canonical IDs, trusted timestamps, authorization, tool selection, SQL, persistence, or final factual answers.

### Ollama
Provides local inference only to Python.

## Trust Rules
Never execute model-generated SQL/URLs, never let the model choose tools, never treat model entity IDs/timestamps as trusted, and never let the model authorize access.

## Clarification
Spring owns clarification state/options. Python only interprets whether a follow-up answers, cancels, or replaces a pending clarification.

## Time
Python returns semantic time. Spring converts it using trusted server time and resolved location timezone.

## Security
Simple development auth/authorization stubs are allowed for the POC, but Spring remains the trusted boundary.

## Data
Synthetic local data only. No real clinician data or PHI.
