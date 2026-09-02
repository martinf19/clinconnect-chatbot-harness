# Definition of Done

## POC Runtime
- no PostgreSQL service required;
- no Redis service required;
- no Docker/Colima/Podman/Compose required;
- H2 backs domain tests;
- Spring in-memory state backs conversation behavior;
- restart loss is accepted POC behavior.

## Replacement Seams
- business services do not depend on H2-specific APIs;
- conversation orchestration does not depend on concrete map/cache classes;
- domain persistence can later use PostgreSQL/source adapter;
- conversation state can later use Redis;
- frontend/API/AI/tool contracts remain unchanged.

## Trust Boundary
Spring owns canonicalization, authorization, execution, domain access, and state; Python does not own trusted execution; React calls Spring only.

## Security
Development auth stub is explicit; Spring trusted boundary is preserved; malformed AI output fails closed; no arbitrary SQL/URL/tool execution.

## Testing
Applicable unit, contract, integration, evaluation, and negative tests pass.
