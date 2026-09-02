# 06 — Conversation Design

## Ownership

Spring owns conversation state and factual response structure.

Python interprets language and pending-clarification replies.

For the POC, Spring in-memory conversation state stores bounded state. Production may use conversation-state store behind the same abstraction.

## Response Statuses

Frontend-visible statuses:

- `ANSWER`
- `CLARIFICATION`
- `NO_MATCH`
- `UNSUPPORTED`
- `ERROR`

Transport-level validation/auth/conflict errors may use HTTP status codes without pretending to be chatbot answers.

## Factual Answer Formatting

Baseline answers are deterministically formatted by Spring.

Example synthetic format:

```text
Dr. Avery Chen is on call for Neurology in Oakland until 7:00 PM.

Pager: 555-0104

## POC Restart Behavior
A Spring Boot restart clears active POC sessions, pending clarifications, and recent idempotency entries. The frontend must tolerate an expired/unknown session and start a new one.
