# Implementation Plan

## Rule
Implement one phase at a time. The POC must run without PostgreSQL, Redis, Docker, Docker Desktop, Colima, Podman, or Compose.

## Phase 0 — Repository and Local POC Foundation
Create React, Spring Boot, and Python scaffolds; H2 config; conversation-state abstraction with in-memory POC implementation boundary; health/config validation; correlation IDs; development auth stub; CORS; intent/tool config loading. No PostgreSQL, Redis, or containers.

Stop.

## Phase 1 — Synthetic Domain Data and Trusted Read Tools
Implement deterministic retrieval entirely with local Java/Spring + H2. Build H2 schema/migrations if used, synthetic fixtures, replaceable repository/service abstractions, all baseline domain services/tools, aliases/canonicalization, development authorization abstraction, injectable Clock, and deterministic factual formatter.

Tests run against H2. Phase 1 has no live PostgreSQL/Redis/container dependency and does not require Python/Ollama for deterministic tool tests.

Stop.

## Phase 2 — AI Interpretation Contract
Select a concrete local Ollama model and implement strict Python interpretation only.

Stop.

## Phase 3 — In-Memory Session, Clarification, and Orchestration
Implement chat endpoint, backend sessions, in-memory state, ownership/idempotency, Python client, H2/config canonicalization, clarification, time normalization, deterministic tool mapping, authorization, execution, state transitions, and factual formatting.

Stop.

## Phase 4 — Multi-Turn Conversation
Implement resume/cancel/unrelated clarification behavior, context, TTL, and pronoun rules through the state abstraction.

Stop.

## Phase 5 — React UI
Implement UI against Spring only.

Stop.

## Phase 6 — Evaluation and Regression
Use deterministic H2 fixture reset and in-memory state tests.

Stop.

## Phase 7 — POC Hardening and Production Adapter Readiness
Verify H2 -> PostgreSQL/source-adapter and in-memory -> Redis replacement seams. Do not implement PostgreSQL/Redis unless explicitly requested.

Stop.

## Phase 8 — Production Readiness Gap Assessment
Document production identity, authorization, source/freshness, PostgreSQL/source adapter, Redis, secrets/TLS, audit, HA/DR, deployment, model governance, and KPIs.
