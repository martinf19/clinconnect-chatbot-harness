# 10 — Evaluation Plan

## Evaluation Layers

### Layer A — AI Interpretation Evaluation

Tests Python interpretation only.

It evaluates:
- canonical intent ID;
- raw parameter extraction;
- semantic time classification;
- declared urgency extraction;
- pending clarification answer classification;
- unsupported detection.

It does not expect canonical DB IDs from the model.

### Layer B — Spring Canonicalization/Orchestration Evaluation

Uses deterministic synthetic fixtures.

It evaluates:
- aliases;
- canonical IDs;
- missing vs ambiguous parameters;
- location unique-or-clarify policy;
- trusted time normalization;
- deterministic intent-to-tool mapping;
- authorization;
- tool results;
- state transitions.

### Layer C — Multi-Turn Conversation Evaluation

Evaluates:
- pending clarification;
- short answer resume;
- cancellation;
- unrelated request replacement;
- last query context;
- single-provider pronoun resolution;
- session expiry/ownership.

### Layer D — Negative/Security Evaluation

Evaluates:
- unsupported clinical classification;
- prompt injection;
- SQL/URL requests;
- hallucination traps;
- malformed model output;
- unauthorized/session-mismatch behavior.

## Evaluation YAML Contract

Every file:
- has `version: 2`;
- has deterministic case IDs;
- uses only documented expected fields;
- avoids placeholder values such as `provider-from-fixture`.

Expected IDs must correspond to planned synthetic fixture IDs.

## Planned Synthetic Fixture IDs

Implementation seed data must provide at minimum:

Locations:
- `loc-oakland`
- `loc-antioch`

Specialties:
- `spec-neurology`
- `spec-cardiology`
- `spec-pediatrics`
- `spec-rheumatology`

Department:
- `dept-oakland-neurology`

Providers:
- `provider-avery-chen`
- `provider-jordan-lee`

The fixtures are fictional.

Coverage fixtures must be created relative to an injectable test clock or fixed integration-test clock, not the workstation wall clock.

## Required Coverage

The evaluation corpus must include:

- current on-call;
- today;
- tonight;
- tomorrow;
- weekend;
- specific date;
- location list;
- specialty list;
- department information;
- contact;
- consult routing;
- explicit urgent/non-urgent routing;
- Chart Chat;
- role explanation;
- aliases;
- missing parameter;
- ambiguous entity;
- ambiguous result;
- no match;
- unsupported request;
- pending clarification answer;
- short invalid answer;
- clarification cancellation;
- unrelated new intent;
- session context;
- session expiry;
- session owner mismatch;
- duplicate message;
- malformed AI structure;
- illegal enum;
- prompt injection;
- authorization denial.

## Acceptance Gates

Business KPI thresholds remain open.

Engineering regression gates for implementation:
- deterministic/unit/contract tests must be 100% passing;
- every supported intent must have at least one positive evaluation;
- every documented negative/security behavior must have at least one test;
- no known hallucinated domain fact is accepted;
- no test may depend on real clinician data.

Interpretation accuracy percentage may be reported, but no business threshold is invented.

## Model Changes

When `OLLAMA_MODEL` changes:
- rerun interpretation evaluations;
- record model name/version/tag;
- compare failures before accepting the change.

## Prompt Changes

Prompt changes require:
- evaluation rerun;
- changed cases documented;
- no silent weakening of unsupported/security cases.

## POC Test Runtime
Deterministic POC integration tests must run without PostgreSQL, Redis, Docker, Colima, Podman, or Compose. Use H2 fixtures, Spring in-memory state, and fixed Spring Clock values.
