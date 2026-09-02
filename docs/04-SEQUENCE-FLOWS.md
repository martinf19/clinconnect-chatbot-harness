# 04 — Sequence Flows

## Flow 1 — New Current On-Call Request

```text
User -> React
React:
  create client_message_id
  POST message + session_id

React -> Spring
Spring:
  authenticate
  validate request
  validate/create session
  verify session ownership
  check idempotency
  create correlation_id
  load bounded conversation-state store state

Spring -> Python /interpret
Python -> Ollama
Ollama -> Python
Python:
  Pydantic validate
  return intent + raw semantic parameters

Python -> Spring
Spring:
  canonicalize specialty/location
  apply BACKEND_UNIQUE_OR_CLARIFY location policy
  decide clarification or execution
  authorize canonical scope
  derive tool from intent
  set trusted reference_time
  execute tool

Spring -> domain data store
domain data store -> Spring

Spring:
  update session state
  deterministic response formatting
  cache response by client_message_id

Spring -> React -> User

## POC Runtime Mapping
For the POC, `conversation-state store` is Spring in-memory state and `domain data store` is H2. Future production may use Redis and PostgreSQL/source adapters behind the same abstractions.
