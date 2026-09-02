# 02 — Requirements

## Scope Rule

Only requirements in the baseline sections are approved for initial implementation.

Mockup-only or candidate features remain deferred unless explicitly promoted.

## Functional Requirements and Acceptance Criteria

### FR-001 Natural-Language Interpretation

The system shall accept natural-language questions for supported intents.

Acceptance criteria:
- every supported utterance resolves to one canonical intent ID or a clarification/unsupported outcome;
- no arbitrary intent ID may be produced;
- unsupported requests do not execute tools.

### FR-002 Location Lookup

The system shall return active supported locations.

Acceptance criteria:
- only active persisted locations are returned;
- no LLM-created location appears in the result.

### FR-003 Specialty Lookup

The system shall return active specialties available at a resolved location.

Acceptance criteria:
- location is required;
- Spring resolves the location to a canonical ID;
- zero canonical matches produces `NO_MATCH`;
- multiple canonical matches produces clarification;
- only persisted location/specialty relationships are returned.

### FR-004 Current On-Call Lookup

The system shall return coverage active at the trusted server reference instant.

Intent rule:
- `CURRENT`, `NOW`, `RIGHT NOW`, and `CURRENTLY` map to `get_oncall_now`;
- an omitted time in a current-coverage question defaults to `CURRENT`.

Location policy:
- specialty is required;
- location is `BACKEND_UNIQUE_OR_CLARIFY`;
- when location is omitted, Spring finds authorized active locations for the canonical specialty;
- exactly one candidate auto-resolves;
- more than one candidate requires clarification;
- zero candidates produces `NO_MATCH`.

Acceptance criteria:
- Spring supplies the reference instant;
- returned assignment must contain the instant in `[starts_at, ends_at)`;
- no result -> `NO_MATCH`;
- multiple valid results that cannot be separated by requested role -> `AMBIGUOUS`;
- the model never chooses a provider.

### FR-005 Scheduled On-Call Lookup

The system shall support:
- `TODAY`
- `TONIGHT`
- `TOMORROW`
- `WEEKEND`
- `SPECIFIC_DATE`

Deterministic routing rule:
- any of the above always maps to `get_oncall_schedule`;
- `TONIGHT` never maps to `get_oncall_now`, even when the current time is inside the tonight interval.

Time interval rules are defined in `docs/08-API-CONTRACTS.md`.

Acceptance criteria:
- Python returns semantic time only;
- Spring creates executable timestamps using resolved location timezone and trusted server time;
- no model-supplied start/end timestamp is executed.

### FR-006 Contact Retrieval

The system shall return approved active contact methods stored for one canonical provider.

Acceptance criteria:
- provider must be explicit or resolve unambiguously from `last_result_context`;
- missing requested contact method is reported as not listed;
- no fallback provider/contact is invented;
- no bulk directory lookup is implied.

### FR-007 Department Information

The baseline shall support a narrow department-information lookup tied to one canonical location and specialty.

Canonical intent:
- `get_department_info`

Baseline persisted/displayable fields:
- department ID;
- department display name;
- location;
- specialty;
- approved informational note, when present.

Explicitly excluded unless later approved:
- clinic hours;
- addresses;
- staff directory;
- injection hours;
- secondary locations;
- broad eConsult content.

Acceptance criteria:
- location and specialty are required;
- only persisted approved fields are returned;
- absent optional fields are omitted;
- the model does not expand department content.

### FR-008 Consult-Routing Information

The system shall retrieve persisted approved consult-routing rules.

Canonical intent:
- `get_pcconsult_info`

Optional filters:
- explicit `time_context`: `DAYTIME`, `AFTER_HOURS`, `WEEKEND`;
- explicit `declared_urgency`: `URGENT`, `NON_URGENT`.

If no time context is explicitly supplied, return all active applicable routing sections rather than guessing the user's current workflow.

Acceptance criteria:
- location and specialty are canonicalized by Spring;
- active/effective rules only;
- routing wording may be deterministically formatted but not semantically altered.

### FR-009 Declared-Urgency Routing

Canonical legacy intent:
- `triage_consult`

Permitted meaning:
retrieve consult routing when the user explicitly states `URGENT` or `NON_URGENT`.

Acceptance criteria:
- user-supplied urgency category is required;
- symptoms/clinical facts must never be converted into an urgency category;
- symptom-based urgency questions return `UNSUPPORTED`.

### FR-010 Chart Chat Guidance

The system shall retrieve approved Chart Chat/secure-message guidance stored for a canonical location/specialty.

Acceptance criteria:
- no guidance is invented;
- absent guidance -> `NO_MATCH`.

### FR-011 Role Explanation

The system shall return the stored approved definition of a known on-call role.

Acceptance criteria:
- exact/approved alias canonicalization only;
- unknown role -> `NO_MATCH`;
- no generated role definition.

### FR-012 Clarification

The system shall clarify missing or ambiguous parameters before execution.

Spring is the final authority on clarification.

Clarification reasons:
- `MISSING_PARAMETER`
- `AMBIGUOUS_ENTITY`
- `AMBIGUOUS_RESULT`
- `LANGUAGE_UNCERTAIN`

Acceptance criteria:
- no tool executes while required clarification remains;
- options come only from Spring/backend data/configuration;
- exactly one pending clarification exists per session;
- pending clarification is resumable as defined in `docs/06-CONVERSATION-DESIGN.md`.

### FR-013 Bounded Session Context

The system shall support safe references to recent validated context.

Examples:
- "How can I reach them?"
- "What about tomorrow?"
- "Same location."

Acceptance criteria:
- context is structured Spring conversation state, not raw model memory (Spring in-memory state for the POC; Redis is the intended production implementation, per NFR-009);
- provider pronouns resolve only when `last_result_context.single_provider_id` exists;
- authorization is re-evaluated every turn;
- expired/mismatched session state is never trusted.

### FR-014 No-Match Handling

When no exact canonical or persisted result exists, the system shall return `NO_MATCH`.

Alternatives may be presented only when Spring explicitly supplies deterministic allowed alternatives.

### FR-015 Approved Aliases

Aliases shall be configuration-driven.

Initial examples:
- `cards` -> `cardiology`
- `cardio` -> `cardiology`
- `neuro` -> `neurology`

No fuzzy matching is enabled by default.

Typos that do not match an approved alias produce clarification/no-match rather than guessed canonical IDs.

## Non-Functional Requirements

### NFR-001 Local POC Execution

All POC components shall run locally from VS Code without containers or external database/state services.

POC runtime:
- H2 for synthetic domain persistence;
- Spring Boot in-memory conversation/session/idempotency store;
- no PostgreSQL requirement;
- no Redis requirement;
- no Docker/Colima/Podman/Compose requirement.

Spring restart loss of active chat/session state is acceptable for the POC.

### NFR-002 Production-Aligned Replacement Seams

The POC shall preserve interfaces allowing H2 -> PostgreSQL/source adapter and in-memory conversation state -> Redis without changing business/API contracts.

### NFR-003 Production-Aligned Trust Boundaries

The local prototype shall preserve the intended production ownership model.

### NFR-004 Trusted Execution

Spring Boot owns authentication, authorization, canonicalization, business logic, persistence access, tool execution, session truth, and final factual response formatting.

### NFR-005 Structured AI Output

Python/FastAPI shall validate LLM output with strict Pydantic models.

Unknown fields/enums/intents cause validation failure.

### NFR-006 Deterministic Tool Selection

The LLM shall not select tools.

Spring maps validated canonical intent IDs to allow-listed tool IDs using configuration.

### NFR-007 Synthetic Data

Initial data shall be synthetic and fictional.

### NFR-008 Observability

Spring shall create a server correlation ID for each newly processed message and propagate it to Python logs/calls.

### NFR-009 Idempotency

Every frontend message shall include a UUID `client_message_id`.

Within a session:
- duplicate ID + identical message returns the cached prior response;
- duplicate ID + different message returns conflict and does not execute.

For the POC, the bounded cache is stored by the Spring in-memory conversation-state implementation. For production, Redis remains the intended implementation.

### NFR-010 Replaceable LLM

Model selection is environment-driven and does not change contracts.

### NFR-011 Production Data Freshness Awareness

Production integration must eventually expose source freshness/effective timestamps and stale-source behavior. The synthetic prototype shall retain effective timestamps where relevant.

## Deferred / Not Approved

- provider-name-only search as a broad directory function;
- provider-centric "next call" search;
- broad past coverage;
- secondary provider locations;
- general phonebook lookup;
- clinic hours/address/staff directory;
- escalation chains;
- coverage gap/open-shift monitoring;
- alerts;
- autonomous recommendations;
- symptom-based clinical triage;
- independent medical advice;
- full eConsult functionality;
- RAG/vector search;
- multi-agent orchestration.

## Remaining Business Questions

1. Final product name.
2. Whether pConsult, pcConsult, and eConsult are business-equivalent terms.
3. Whether supplied Rheumatology examples that reference Dermatology routing are correct.
4. Exact production authorization roles/attributes.
5. Exact production source systems and freshness expectations.
6. Formal business KPI for search-time/click reduction.
7. Whether deferred provider-centric scheduling capabilities should enter scope.
8. Whether production "tonight" and "weekend" boundaries differ from the prototype conventions documented in API contracts.
