# 08 — API Contracts

## Contract Principles

- all machine JSON/YAML field names use `snake_case`;
- Spring is the only frontend API;
- Python returns interpretation, never executable tool decisions;
- Spring canonicalizes entities/time;
- Spring derives tool ID from canonical intent ID;
- model output is Pydantic validated;
- unknown fields/enums fail validation;
- canonical IDs are produced only by Spring/backend sources.

## Canonical Enums

### interpretation_status

- `INTERPRETED`
- `NEEDS_LANGUAGE_CLARIFICATION`
- `UNSUPPORTED`

### frontend response status

- `ANSWER`
- `CLARIFICATION`
- `NO_MATCH`
- `UNSUPPORTED`
- `ERROR`

### time_expression.kind

- `CURRENT`
- `TODAY`
- `TONIGHT`
- `TOMORROW`
- `WEEKEND`
- `SPECIFIC_DATE`

### time_context

- `DAYTIME`
- `AFTER_HOURS`
- `WEEKEND`

### declared_urgency

- `URGENT`
- `NON_URGENT`

### contact_type

- `MOBILE`
- `OFFICE`
- `TIE_LINE`
- `PAGER`
- `CHART_CHAT`
- `BACKLINE`

## Clarification Parameter Names

`clarification.parameter` must use the same interpretation parameter key that is being resolved.

Allowed values are:

- `location_text`
- `specialty_text`
- `provider_reference`
- `role_text`
- `contact_type`
- `time_expression`
- `time_context`
- `declared_urgency`

Spring may store already-resolved canonical values separately as `*_id`/`role_code`.

## Frontend -> Spring

### POST /api/v1/chat/messages

Request:

```json
{
  "session_id": null,
  "client_message_id": "7db0a8f3-7e76-49dc-a761-87f03b2c2d3d",
  "message": "Who is covering Neurology in Oakland?"
}

## POC Infrastructure Note
API contracts are infrastructure-neutral. H2 backs POC domain repositories; Spring in-memory state backs POC sessions/clarifications/idempotency. Future production may use PostgreSQL/source adapters and Redis without API changes.
