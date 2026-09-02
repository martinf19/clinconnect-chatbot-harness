# 03 — Architecture

## Principle
The LLM interprets language; Spring decides what can be canonicalized, authorized, and executed.

## Local POC
- React + TypeScript + Vite
- Spring Boot
- Python + FastAPI + Pydantic
- Ollama
- H2 embedded relational database
- Spring in-memory conversation/session/idempotency store
- no containers

## Future Production Target
- React
- Spring Boot trusted backend
- Python AI service
- approved model endpoint
- PostgreSQL for relational persistence where appropriate
- Redis for bounded state

## Required Abstraction Boundaries
```text
Domain repository/service abstraction
  POC -> H2
  Production -> PostgreSQL or approved source adapter

Conversation-state abstraction
  POC -> Spring in-memory
  Production -> Redis
```

## Ownership
React owns UI only. Spring owns auth boundary, authorization, canonicalization, time, ambiguity/clarification, tool mapping/execution, domain access, state access, and response formatting. Python owns language interpretation only. Ollama serves Python only.

## POC Request Ordering
1. establish development identity;
2. validate/create in-memory session;
3. verify session owner;
4. check in-memory idempotency;
5. establish capability scope;
6. call Python interpretation;
7. canonicalize against config/H2;
8. decide clarification vs execution;
9. authorize canonical scope;
10. derive tool from intent;
11. execute through Spring services/repositories;
12. update in-memory state;
13. format response.

Production preserves this order while replacing development identity, H2, and in-memory state.

## Production Concerns
Production documentation continues to require enterprise identity, authorization, source freshness, PostgreSQL/source adapter, Redis, secrets/TLS, audit/retention, HA/DR, model governance, and observability.
