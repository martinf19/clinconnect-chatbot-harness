# 11 — Local Development

## Goal
Run the complete POC from VS Code without containers or external database/state services.

## Runtime
Host processes: React/Vite, Spring Boot, Python/FastAPI, Ollama.
Embedded/in-process: H2 and Spring in-memory conversation/session/idempotency state.

Not required: PostgreSQL, Redis, Docker, Docker Desktop, Colima, Podman, Compose.

## Prerequisites
- VS Code
- Git
- Java 21 LTS
- Maven Wrapper once scaffolded
- Node.js active LTS, minimum 22
- npm
- Python 3.12+
- `uv`
- native Ollama

## Spring Boot POC Dependencies
Spring Web, validation, Actuator, persistence/JPA support, H2, and optionally Flyway. Do not require PostgreSQL/Redis services or drivers for the POC.

## H2
Prefer file-backed H2 for convenience; in-memory H2 is acceptable if startup fixtures recreate data deterministically. Avoid H2-specific business SQL where practical.

## In-Memory Conversation State
Use a Spring-managed thread-safe bounded store supporting session owner, TTL, context, pending clarification, and idempotency cache. Restart loss is accepted.

## Ports
React 5173, Spring 8080, Python 8000, Ollama 11434. No database/state service ports are needed.

## Startup
1. Ollama
2. Spring Boot
3. Python AI service
4. React/Vite

## Health
Spring Actuator, Python `/health`, and Ollama diagnostic. No PostgreSQL/Redis health checks.

## Production Replacement Path
Future production may replace H2 -> PostgreSQL/source adapter, in-memory state -> Redis, and development auth -> enterprise identity/authorization without changing external contracts.
