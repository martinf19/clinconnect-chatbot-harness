# 09 — Security

## Primary Rule

The LLM is untrusted interpretation infrastructure.

Spring Boot is the trusted security and execution boundary.

## Mandatory Request Security Ordering

Before Python interpretation:
1. authenticate caller;
2. validate request;
3. validate/create session;
4. verify session owner;
5. check duplicate message;
6. confirm caller may use chatbot capability.

Before tool execution:
7. canonicalize requested entities/time;
8. authorize the canonical resource scope;
9. derive allow-listed tool from intent;
10. validate tool arguments;
11. execute.

## Session Security

`session_id` is opaque and backend-issued.

Spring must store the authenticated owner in its conversation-state store: Spring in-memory state for the POC; Redis is the intended production implementation (see "POC Conversation State" and "Future Production State" below).

Every supplied `session_id` must match the authenticated subject.

Mismatch:
- fail closed;
- no state disclosure;
- no AI call;
- no tool execution.

Session state never grants authorization by itself.

Authorization is re-evaluated every turn.

## Idempotency Security

`client_message_id` prevents accidental duplicate inference/execution.

Same ID with changed content is suspicious/invalid and returns conflict.

## AI Output Security

Pydantic models must:
- forbid unknown fields;
- enforce enum values;
- enforce supported intent IDs;
- enforce bounded lengths;
- validate conditional fields such as `specific_date`.

Spring must not execute:
- SQL;
- URLs;
- paths;
- class names;
- method names;
- tool IDs
from model output.

The model does not output tool IDs in the baseline contract.

## Canonical IDs

The model never outputs canonical:
- location IDs;
- specialty IDs;
- department IDs;
- provider IDs;
- role codes as trusted execution values.

Spring obtains them from configuration/persistence/session truth.

## Tool Security

Intent-to-tool mapping is static configuration loaded by Spring.

A missing/unknown mapping is a deployment/configuration error and fails closed.

## Database Security

For the POC, Spring owns H2 access and no external database credentials are required.

Use:
- least-privileged application account;
- parameterized repository/query code;
- migration account separation later if enterprise policy requires it.

Python/React/Ollama do not access the database directly.

## Network Security

Model output never selects an endpoint.

Local service URLs come from configuration.

Production requires:
- TLS;
- service-to-service authentication as enterprise policy requires;
- secret manager rather than `.env`.

## Clinical Safety Boundary

The system must not infer clinical urgency from symptoms.

`triage_consult` accepts only explicit declared urgency.

Tests must verify symptom descriptions do not become `URGENT`/`NON_URGENT` routing execution.

## Logging

Baseline structured logs:
- timestamp;
- service;
- correlation ID;
- session ID or safe derived identifier;
- authenticated subject identifier according to local privacy policy;
- intent ID;
- tool ID chosen by Spring;
- result status;
- latency;
- safe error category.

Do not log:
- secrets;
- database credentials;
- unrestricted raw prompts by default;
- real PHI in the synthetic prototype.

## Correlation IDs

Spring generates the authoritative correlation ID.

The same ID is propagated to:
- Python request;
- tool execution logs;
- frontend response.

Duplicate cached messages return their original correlation ID.

## POC Conversation State

Spring in-memory state contains ephemeral POC state only.

Controls:
- TTL;
- ownership field;
- bounded processed-message cache;
- no unrestricted transcripts.

## CORS

Restrictive local origin from environment configuration is required when frontend API is introduced.

Do not defer basic CORS safety to a late hardening phase.

## Secrets

Startup must fail with a clear configuration error if mandatory secrets/config are absent.

`.env.example` contains placeholders only.

## Production Concerns to Address Before Real Data

- enterprise SSO/OIDC/SAML integration;
- production authorization policy/attributes;
- TLS;
- service identities;
- secrets manager;
- data classification;
- retention;
- audit retention;
- encryption at rest;
- vulnerability/container scanning;
- source freshness/staleness behavior;
- HA/DR;
- model and prompt governance.

## Required Security Tests

Before tool orchestration is complete, tests must cover:
- session-owner mismatch;
- unauthorized capability;
- unauthorized canonical resource;
- unknown intent mapping;
- malformed AI output;
- prompt injection cannot cause tool selection;
- SQL-like input cannot execute SQL;
- arbitrary URL cannot be called;
- duplicate message conflict;
- cross-session provider-context isolation.

## Future Production State
Redis remains the intended production state implementation behind Spring. PostgreSQL/source-system credentials remain Spring-side only.

## POC Security Stubs
Simple development authentication/authorization stubs are allowed, but Spring remains the trusted security boundary.
