# 07 — Data Model

## Local POC
H2 stores synthetic domain data. Spring memory stores session/conversation/idempotency state.

## Future Production
PostgreSQL remains the intended relational persistence implementation where appropriate. Redis remains the intended bounded state implementation.

## Relational Domain Model
The H2 POC schema mirrors the target relational concepts: location, specialty, department, location_specialty, provider, provider_specialty, contact_method, on_call_role, coverage_assignment, and consult_routing_rule. Avoid H2-specific business behavior so PostgreSQL/source adapters can replace it later.

## POC Conversation State
State remains conceptually identical to the existing contract: session owner, timestamps, last query context, last result context, one pending clarification, and bounded processed-message/idempotency entries.

The concrete implementation must be Spring-managed, thread-safe, bounded, TTL-aware, and owner-aware.

## State Transitions
- session creation: owner/timestamps/empty context;
- clarification: update pending clarification/activity only;
- success: clear clarification, replace query/result context, cache response;
- NO_MATCH: update query, clear result, cache response;
- UNSUPPORTED: preserve prior query/result, cache response;
- validation/system error: no query/result change;
- cancellation: clear pending clarification only.

## Restart Behavior
All POC conversation/session/idempotency state may be lost when Spring restarts. This is accepted.

## Replacement Requirement
Conversation-state contracts must allow Redis later without external contract changes. Domain repository/service contracts must allow PostgreSQL/source adapters later without chatbot contract changes.
