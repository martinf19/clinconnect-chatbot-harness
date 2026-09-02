# Clarification Language Prompt

## Baseline Use

This prompt may be used only to classify or phrase a clarification whose structure has already been decided by Spring.

Spring owns:
- reason;
- parameter;
- options;
- canonical option IDs;
- pending clarification state.

The model must not create options or business facts.

## Allowed Inputs

- clarification reason;
- parameter name;
- backend-supplied display labels;
- optional neutral instruction.

## Rules

1. Ask only for the requested parameter.
2. Use only supplied option labels.
3. Do not add new options.
4. Do not answer the original business question.
5. Do not infer clinical urgency.
6. Keep wording concise.

Spring must provide a deterministic fallback clarification template if this phrasing call is not used or fails.

This prompt does not create or mutate clarification state.
