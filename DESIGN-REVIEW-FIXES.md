# Design Review Fixes Applied

## Critical Fixes

1. FR-007 now has an explicit `get_department_info` intent/tool with narrow approved fields.
2. Time routing is deterministic: CURRENT -> `get_oncall_now`; TODAY/TONIGHT/TOMORROW/WEEKEND/SPECIFIC_DATE -> `get_oncall_schedule`.
3. Conditional location is owned by Spring through `BACKEND_UNIQUE_OR_CLARIFY`.
4. Python returns semantic time; Spring computes executable timestamps from trusted clock + location timezone.
5. Spring owns clarification state/options; Python only interprets language and clarification replies.
6. Session IDs are backend-issued and bound to authenticated subjects; mismatch fails before AI calls.
7. Request ordering is explicitly authentication/session ownership before interpretation, and canonical resource authorization before execution.
8. AI no longer selects tools; Spring deterministically maps intent -> tool.
9. Strict structured interpretation schema is defined; confidence removed from execution contracts.
10. Local runtime now has one recommended host/Colima profile, eliminating container `localhost` ambiguity.
11. Baseline security controls were moved into the phases where their execution surfaces are introduced.
12. Evaluation YAML moved to a deterministic v2 contract with fixed fixture IDs and expanded coverage.

## Additional Important Fixes

- `triage_consult` explicitly means routing by user-declared urgency, not clinical triage.
- Baseline final factual responses are deterministic Spring formatting; LLM response generation is reserved/deferred.
- Redis state transitions, pending clarification schema, idempotency, and context replacement rules are explicit.
- PostgreSQL persistence boundary is explicit and now includes narrow department records.
- Java/Maven, Python/uv, PostgreSQL/Redis versions, native Ollama mode, Flyway, and fixed test clock are specified.
