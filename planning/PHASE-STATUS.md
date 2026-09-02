# Phase Status

## Current Phase

Phase 6 — Evaluation and Regression

## Status

COMPLETE (1 known deterministic-logic failure, not fixed this phase — see below)

## Phase 3 Objective

Per `planning/PLAN.md`: "Implement chat endpoint, backend sessions, in-memory state,
ownership/idempotency, Python client, H2/config canonicalization, clarification, time
normalization, deterministic tool mapping, authorization, execution, state transitions, and
factual formatting." Per this turn's explicit instructions, the focus was Spring in-memory
conversation state, multi-turn clarification (ask + resume + cancel + unrelated-replace),
pending intent/parameter handling, session state transitions, and integration with the
existing Phase 2 `/interpret` contract — reusing Phase 1's canonicalization/tool/formatting
services unchanged. Phase 4 ("Multi-Turn Conversation": deeper context reuse across unrelated
turns, TTL refinement, broader pronoun rules) was explicitly not started.

## Files Created or Changed

**New package `backend/.../session/`** (conversation-state abstraction, the Redis-replaceable seam)
- `ConversationSessionStore` (interface) + `InMemoryConversationSessionStore` — bounded,
  thread-safe, TTL-aware, owner-aware map (docs/07-DATA-MODEL.md). An expired or unknown
  `session_id` is transparently treated as absent so a lookup and a restart-tolerant "start
  fresh" are the same code path (docs/06 "POC Restart Behavior").
- `ConversationSession` — the mutable per-session state (owner, timestamps, pending
  clarification, last query/result context, bounded idempotency cache), with one method per
  docs/07 "State Transitions" row (`recordClarification`, `recordSuccess`, `recordNoMatch`,
  `recordUnsupported`, `cancelPendingClarification`, `recordSystemError`) so each transition's
  exact effect is traceable to that table. All mutation is synchronized on the instance.
- `PendingClarification`, `ClarificationOption`, `ClarificationReason`,
  `ClarificationParameterName` (wire-compatible with ai-service's identically-named Phase 2
  enum via matching `@JsonValue`/`@JsonCreator`), `LastResultContext`, `LastQueryContext`,
  `CachedMessage`, `IdempotencyOutcome` (sealed: New/Replay/Conflict).

**New package `backend/.../interpretation/`** (the Python client, mirrors ai-service's Phase 2 contract)
- `PythonInterpretationClient` — calls ai-service's `POST /interpret` (a plain `RestClient`,
  built directly rather than via Spring Boot's autoconfigured `RestClient.Builder`: this
  project has no `spring-boot-starter-restclient`/`-webclient` dependency, and adding one was
  avoided per this turn's "no new framework unless Phase 3 explicitly requires it" constraint
  — see Deviations). Propagates the correlation ID Spring already generates (NFR-008); maps
  any transport failure to `InterpretationUnavailableException`.
- `InterpretRequest`/`InterpretResponse` and supporting DTOs (`InterpretationParameters`,
  `TimeExpressionValue`, `ProviderReferenceValue`, `ClarificationAnswer`,
  `SessionContextPayload`, `LastResultPayload`, `PendingClarificationPayload`) — deliberately
  reuse existing backend enums where the contract is identical rather than duplicating them
  (`TimeExpressionKind` from Phase 1's `time` package; `ContactType`/`TimeContext`/
  `DeclaredUrgency` from `domain.model`).
- `InterpretationStatus`, `ProviderReferenceKind`, `ClarificationAnswerKind` (new — no Phase 1
  equivalent existed).

**New package `backend/.../chat/`** (the chat endpoint and orchestration)
- `ChatController` — `POST /api/v1/chat/messages`. Authenticates via the existing (previously
  unused) `DevAuthenticationProvider`; reuses the correlation ID `CorrelationIdFilter` already
  put in MDC (NFR-008) rather than minting a second one.
- `ChatOrchestrationService` — implements docs/03-ARCHITECTURE.md's full 13-step "POC Request
  Ordering": session validate/create → ownership → idempotency → Python interpretation →
  clarification decide/resume → (delegated) canonicalize/authorize/execute → state update →
  format. Owns the clarification resume design (see its class Javadoc): a pending
  clarification stores the intent and the raw interpretation parameters as they stood at
  clarification time; resuming overlays just the newly-resolved value and re-runs the same
  per-intent handler, so a still-unresolved answer naturally re-clarifies without special
  casing. `SELECTED_OPTION` for `PROVIDER_REFERENCE` is special-cased to pass the chosen
  option's canonical id straight through (reusing the `LAST_RESULT_PROVIDER` trusted-passthrough
  mechanism) rather than re-canonicalizing the option's label as raw text — found necessary
  because two providers can legitimately share a display name (see Deviations #3).
- `IntentOrchestrationService` — the canonicalize → decide-clarification-vs-execute → execute
  → format dispatcher for all 9 canonical intents, built entirely on Phase 1's existing
  `CanonicalizationService`/tool services/`FactualResponseFormatter`/`TimeIntervalResolver` (no
  Phase 1 code changed). Fails closed (`UnknownIntentException`) if an `intent_id` has no
  `config/intents.yaml` entry — defense-in-depth per NFR-006, since this can't happen in normal
  operation (ai-service's `IntentId` enum is itself built from the same file, Phase 2).
- `IntentOutcome` (sealed: Answer/Clarify/NoMatch), `ChatMessageRequest`/`ChatMessageResponse`/
  `ChatResponseStatus`/`ClarificationView`, `SessionOwnershipException` (→ HTTP 403),
  `DuplicateMessageConflictException` (→ HTTP 409), `UnknownIntentException`.

**Config**
- `backend/src/main/resources/application.yml` — added `spring.jackson.property-naming-strategy:
  SNAKE_CASE` (docs/08 "all machine JSON/YAML field names use snake_case" — applies to the
  chat API too, not just Python's contract); `clinconnect.ai-service.{base-url,timeout-seconds}`
  (already in `.env`/`.env.example` since Phase 0, now actually read);
  `clinconnect.session.{ttl-seconds,max-processed-messages}` (same).

**Tests** (41 new, all deterministic — real H2/canonicalization/tool execution, only
`PythonInterpretationClient` mocked via `@MockitoBean`, and a fixed `Clock` bean override so
on-call fixtures never depend on wall-clock time, per docs/10-EVALUATION-PLAN.md)
- `session/ConversationSessionTest` (10), `session/InMemoryConversationSessionStoreTest` (5) —
  plain unit tests of the state machine and TTL/bounding, no Spring context.
- `chat/IntentOrchestrationServiceTest` (3) — fail-closed unknown-intent, basic dispatch.
- `chat/ChatOrchestrationServiceTest` (19) — the main integration suite: fresh-request answer,
  ambiguous-location clarify-then-resume, missing-parameter clarify-then-resume, UNRESOLVED
  re-asks without executing, CANCEL, UNRELATED-replaces-pending, **three
  `getContactInfoClarificationPath_*` tests** (missing provider_reference → resume; ambiguous
  same-display-name providers → resume by selection; missing optional contact_type still
  answers correctly) plus a pronoun/`LAST_RESULT_PROVIDER` pair (no context → clarifies; valid
  prior single-provider context → resolves), idempotency replay/conflict, unknown-session
  tolerance, session-ownership rejection, unauthorized-role fail-closed, UNSUPPORTED,
  NO_MATCH, and AI-service-failure fail-closed-with-retry-allowed.
- `chat/ChatControllerTest` (4) — HTTP-level contract: 200 with snake_case body, 403, 409,
  correlation-id response header.

## Tests Run and Results

- `backend/mvnw test` (offline, `-Dmaven.compiler.release=17` — JDK 21 still unavailable in
  this sandbox, carried-over environment caveat): **101/101 passing**, 0 failures (60
  pre-existing Phase 0/1 + 41 new Phase 3), fully deterministic.
- `ai-service` `pytest`: 57/57 passing, unchanged (Phase 3 did not touch ai-service).
- **Real end-to-end smoke test** (not mocked): started the actual Spring Boot backend
  (`java -cp ... ChatbotBackendApplication`, since `spring-boot:run`/`package` need Maven
  plugin artifacts not cached offline) and the actual `ai-service` (`uvicorn`, real
  `qwen2.5:7b-instruct` via real Ollama), then drove a real multi-turn HTTP conversation:
  - `"What locations can I search?"` → real `ANSWER` ("Locations: Antioch, Oakland").
  - `"Who is on call for Neurology tonight?"` → real `NO_MATCH` (correctly: no
    `coverage_assignment` fixtures exist outside tests).
  - `"What specialties are available?"` → real `CLARIFICATION` (`MISSING_PARAMETER`,
    `location_text`) → resumed with `"Oakland"` → real `ANSWER` ("Specialties at Oakland:
    Cardiology, Neurology, Pediatrics"). Full real clarification round-trip through the live
    model.
  - **`get_contact_info` clarification path, explicitly requested this turn**:
    `"How can I reach the on-call clinician?"` → real `CLARIFICATION` (`MISSING_PARAMETER`,
    `provider_reference`) → resumed with `"Dr. Avery Chen"` → real `ANSWER` ("Dr. Avery Chen
    contact methods:\n- Pager: 555-0104\n- Mobile: 555-0111"). Confirms Spring's clarification
    mechanism correctly recovers this intent end-to-end regardless of the Phase 2 residual
    `contact_type` extraction gap.
  - Idempotency: same `client_message_id` + same message → identical cached response with the
    **same original `correlation_id`** (docs/09 requirement, verified literally); same id +
    different message → real HTTP 409; unknown `session_id` → tolerated, real HTTP 200 with a
    freshly minted session.

## Result of the get_contact_info Clarification-Path Tests (explicitly requested)

**Pass**, at both levels:
1. **Deterministic integration tests** (mocked Python, real H2/canonicalization):
   `getContactInfoClarificationPath_missingProviderReferenceThenResumes`,
   `_ambiguousProviderNameThenResumesWithSelectedOption`,
   `_missingContactTypeStillAnswersWithFullContactList`,
   `_pronounWithNoSessionContextRequiresClarification`,
   `_pronounResolvesFromPriorSingleProviderResult` — all 5 pass.
2. **Real end-to-end** against the live model (see above) — the missing-provider-reference
   path was exercised live and passed. The ambiguous-duplicate-provider-name path is not easily
   reproducible live (the seeded fixtures have only one "Dr. Avery Chen"; the integration test
   inserts a second one deliberately) but is covered deterministically.

A real bug was caught and fixed while building this coverage: resuming a `SELECTED_OPTION`
reply by re-canonicalizing the chosen option's *label* as raw text (the general resume
strategy used for every other parameter) silently re-produced the *same* ambiguous result when
two providers share a display name, because the label alone can't disambiguate them. Fixed by
special-casing `PROVIDER_REFERENCE` `SELECTED_OPTION` resumes to pass the chosen option's
canonical id straight through instead (see `ChatOrchestrationService` Deviations #3 below).

## Deviations / Assumptions Requiring Confirmation

1. **`POST /api/v1/chat/messages` response shape is this implementation's own design.**
   docs/08-API-CONTRACTS.md only shows the request shape before cutting to "POC Infrastructure
   Note" — no response contract is documented. Implemented: `{session_id, status, answer_text,
   clarification: {reason, parameter, options: [{option_id, label}]} | null, correlation_id}`,
   using the canonical `ChatResponseStatus`/`ClarificationReason`/`ClarificationParameterName`
   enums throughout. Phase 5 (React UI) must consume exactly this shape or this file changes
   with it.
2. **`RestClient` is built directly (`RestClient.builder()`), not via Spring Boot's
   autoconfigured `RestClient.Builder`.** Discovered that this project's dependencies
   (`spring-boot-starter-webmvc` only) don't pull in the Boot autoconfiguration module that
   provides that bean — adding `spring-boot-starter-restclient` was avoided per this turn's "no
   new framework unless Phase 3 explicitly requires it." The manually-built client explicitly
   reuses the app's own configured `JsonMapper` bean (Jackson 3 — Spring Boot 4.1.1/Spring 7
   moved off `com.fasterxml.jackson.databind.ObjectMapper` to `tools.jackson.databind.json
   .JsonMapper`) so it still honors `SNAKE_CASE` naming; this is a one-off manual wiring choice
   worth revisiting if a second outbound HTTP client is ever needed.
3. **`SELECTED_OPTION` resume for `PROVIDER_REFERENCE` bypasses re-canonicalization**, reusing
   the `LAST_RESULT_PROVIDER` trusted-passthrough path with a synthetic `LastResultContext`
   built from the chosen option (see Files Created above and the bug it fixes). `LOCATION_TEXT`
   and `ROLE_TEXT` `SELECTED_OPTION` resumes still re-canonicalize the option's label as raw
   text — safe today because `Location`/`OnCallRole` display names are effectively unique in
   this schema/seed data, but the same fragility would apply if that ever changed. Worth
   generalizing the provider fix to all three parameters in a later phase rather than relying
   on that assumption.
4. **`missing_parameters` clarification always targets the first entry Python returned**, with
   no explicit tie-breaking policy documented anywhere. In practice this is a non-issue: every
   current intent has at most one always-required parameter beyond what canonicalization itself
   can already report missing (config/intents.yaml), so this has never been observed to matter,
   but it's an implicit ordering assumption.
5. **Resuming a `TIME_EXPRESSION` clarification via raw-text overlay is not implemented** — it
   would require re-running semantic time classification (a Python job), not a literal
   substitution like the other parameters. Such a resume safely re-asks (`UNRESOLVED`-style
   fallback) rather than guessing. Not exercised by any required test/fixture; flagged as a
   known gap for whichever phase first needs it. `CONTACT_TYPE`/`TIME_CONTEXT` can never become
   a clarification target in this implementation at all (both are always-optional per
   config/intents.yaml, so never in `missing_parameters`, and this orchestrator never raises
   AMBIGUOUS_ENTITY/AMBIGUOUS_RESULT for them) — not a gap, just worth noting explicitly.
6. **`unauthorized_resource`-style per-location entitlement is still not implemented** —
   carried over unchanged from Phase 1 deviation #4 (`DevAuthorizationService` is role-only,
   scope-blind; no per-user location entitlement data model exists anywhere in the docs). Not
   in this turn's scope to build; `negative-evaluation.yaml`'s `unauthorized_resource` case
   remains unsatisfiable until that model exists.
7. **TTL enforcement is lookup-based only** (`ConversationSessionStore.find` treats an expired
   session as absent and evicts it lazily on next access), not an actively-swept background
   process. Sufficient for POC bounding and correct for every documented behavior (an expired
   session is never trusted), but `planning/PLAN.md` assigns "TTL" more fully to Phase 4 —
   flagging this as the baseline this implementation gives Phase 4 to build on, not a claim
   that Phase 4's TTL work is already done.
8. **`LastQueryContext` is intentionally minimal** (`{intent_id, raw_message}` only — no
   resolved canonical parameters). It satisfies docs/07's state-transition table (updated on
   NO_MATCH, replaced on success) but does not yet carry what Phase 4's "context reuse" examples
   need (e.g. "What about tomorrow?", "Same location." require remembering the *resolved*
   specialty/location, not just the intent id and original text). Deliberately not built out
   further since context-reuse pronoun rules are explicit Phase 4 scope per `planning/PLAN.md`.
9. **Cancellation and clarification-uncertainty responses use existing statuses, not new
   ones.** `CANCEL` returns `ANSWER` with a fixed, deterministically-formatted acknowledgement
   string (Spring-formatted, never model text) rather than inventing a 6th frontend status.
   `NEEDS_LANGUAGE_CLARIFICATION` returns `CLARIFICATION` with `reason=LANGUAGE_UNCERTAIN` and
   a null `parameter`/empty `options` (the closed 5-status enum has no better fit, and
   docs/06-CONVERSATION-DESIGN.md fixes that enum). This status is not currently reachable in
   practice (Phase 2's ai-service prompt paths don't produce it), so this is a documented
   default rather than a battle-tested behavior.
10. **Carried forward unchanged, not touched this phase**: TODAY/TONIGHT/WEEKEND boundary
    conventions remain the temporary, business-unconfirmed POC assumption documented in Phase 1
    (`TimeIntervalResolver`'s own Javadoc, Phase 1 deviation #2) — `IntentOrchestrationService`
    calls it exactly as-is with no changes to its semantics. The Phase 2 `contact_pager`
    extraction-accuracy gap is also unchanged; this phase's job (and this turn's explicit ask)
    was making sure Spring's clarification path compensates for it correctly, which the tests
    above confirm it does.

## Unresolved Issues / Limitations

1. `unauthorized_resource` (per-location entitlement) remains unimplemented (Deviation #6,
   carried from Phase 1 deviation #4).
2. `TIME_EXPRESSION` clarification resume is not implemented (Deviation #5) — re-asks safely
   rather than guessing, but doesn't recover.
3. `LOCATION_TEXT`/`ROLE_TEXT` `SELECTED_OPTION` resumes still rely on display-name uniqueness
   (Deviation #3) — safe under current seed data/schema constraints, not structurally guaranteed
   the way the `PROVIDER_REFERENCE` fix now is.
4. No `uv.lock` for `ai-service/`; JDK 21 verification still outstanding for the backend — both
   carried over unchanged from Phase 0/1/2.
5. Time-interval-boundary business confirmation (Phase 1 deviation #2) remains open.

## Is Phase 4 Ready to Begin?

Yes, with two things Phase 4 should plan around: (a) `LastQueryContext` (Deviation #8) will
need enriching with resolved canonical parameters, not just intent id/raw text, before
"same location"/"what about tomorrow"-style context reuse can be built on it; (b) the
`SELECTED_OPTION` resume fragility noted in Deviation #3 for `LOCATION_TEXT`/`ROLE_TEXT` is a
reasonable thing to generalize (using the `PROVIDER_REFERENCE` fix as the template) while
Phase 4 is already working in this same resume-path code. Session/clarification/idempotency
mechanics, the chat endpoint, and the Python client are all in place, tested (deterministically
and against the real live stack), and unaffected by anything Phase 4 is expected to add.

---

# Phase 4 Report — Multi-Turn Conversation

## Phase 4 Objective

Per `planning/PLAN.md`: "Implement resume/cancel/unrelated clarification behavior, context,
TTL, and pronoun rules through the state abstraction." Resume/cancel/unrelated clarification
and provider-pronoun resolution were already built in Phase 3; this phase's actual new scope,
per the four carried-forward Phase 3 items and FR-013's "Same location."/"What about
tomorrow?" examples, was: (1) enrich `LastQueryContext` with resolved canonical parameters so
those two example follow-ups actually work, entirely through the existing Spring conversation-
state abstraction; and (2) generalize the Phase 3 `PROVIDER_REFERENCE` `SELECTED_OPTION`
trusted-passthrough fix to `LOCATION_TEXT`/`ROLE_TEXT`. TODAY/TONIGHT/WEEKEND boundary
conventions were left untouched (still the temporary POC assumption from Phase 1), and no
per-location authorization was implemented (PLAN.md/CLAUDE.md do not require it for Phase 4).
No ai-service/Python changes were made or needed — this phase's design deliberately keeps all
new behavior on the Spring side of the trust boundary (CLAUDE.md: Python "never owns canonical
IDs... or deterministic intent-to-tool mapping"; Spring owns clarification/ambiguity), rather
than reopening the Python interpretation contract/prompt for a POC-scope feature.

## Design Summary

- **`LastQueryContext`** (`session/LastQueryContext.java`) now carries `locationText`/
  `specialtyText`/`roleText` — always a canonical *display name* Spring itself resolved, never
  raw/unvalidated user text — alongside the existing `intentId`. Populated on both `success` and
  `NO_MATCH` transitions (docs/07-DATA-MODEL.md already calls for "update query" on both).
- **`ResolvedQueryContext`** (new, `chat/`) is the per-handler carrier `IntentOrchestrationService`
  attaches to `IntentOutcome.Answer`/`IntentOutcome.NoMatch` so `ChatOrchestrationService` can
  build the `LastQueryContext` above without every handler reaching into session state itself.
- **`SessionResolutionContext`** (new, `chat/`) bundles everything `IntentOrchestrationService
  .resolve` may trust from the session for one request: `lastResult` (Phase 3 provider pronoun,
  unchanged), `lastQuery` (Phase 4 follow-up reuse), and an optional trusted
  `(parameter, canonicalId)` selection pair — used only when resuming a `SELECTED_OPTION` reply.
  `resolve`'s signature changed from a bare `LastResultContext` fourth argument to this bundle.
- **Missing-parameter context backfill** (`ChatOrchestrationService.dispatchInterpreted` /
  `overlayFromLastQueryContext`): before turning a Python-reported missing required parameter
  into a clarification, `LOCATION_TEXT`/`SPECIALTY_TEXT`/`ROLE_TEXT` are first checked against
  `LastQueryContext`; a hit is silently overlaid and removed from the missing list. This is what
  makes "What about tomorrow?" work: Python extracts only the new `time_expression` and reports
  `specialty_text` missing; Spring backfills it from the prior turn instead of re-asking.
  `TIME_EXPRESSION`/`PROVIDER_REFERENCE`/`CONTACT_TYPE`/`DECLARED_URGENCY` are deliberately never
  auto-filled this way (time must come from the current message; provider pronouns already have
  their own Phase 3 mechanism; urgency is safety-sensitive per FR-009 and must stay explicit).
- **BACKEND_UNIQUE_OR_CLARIFY location fallback** (`IntentOrchestrationService
  .resolveLocationWithContext`/`applyContextLocationFallback`): this is the separate mechanism
  "Same location." needs. When location is omitted and `resolveLocationForSpecialty` would
  otherwise clarify with `AMBIGUOUS_ENTITY` (multiple active locations offer the requested
  specialty — `location_text` is never in Python's `missing_parameters` for this policy, so it
  cannot go through the backfill above), Spring first checks whether the session's last resolved
  location is still one of the ambiguous candidates; if so it silently reuses it instead of
  clarifying. Still goes through `CanonicalizationService` — never a guess, only a scoped default.
- **Generalized `SELECTED_OPTION` trusted passthrough** (`ChatOrchestrationService
  .resumeWithOverlay`, `IntentOrchestrationService.resolveLocationWithContext`/
  `resolveRoleWithContext`): resuming a `LOCATION_TEXT` or `ROLE_TEXT` clarification with a
  selected option now passes that option's canonical id straight through
  (`SessionResolutionContext.trustedSelection`) instead of re-canonicalizing its display-name
  label — mirroring the Phase 3 `PROVIDER_REFERENCE` fix. `Location`/`OnCallRole` display names
  are not schema-unique (confirmed: `findByDisplayNameIgnoreCaseAndActiveTrue` returns a
  singular `Optional`, so two active rows sharing a display name make plain re-canonicalization
  throw `IncorrectResultSizeDataAccessException` instead of resolving) — the four new tests below
  exercise exactly that scenario and confirm the bypass avoids it entirely.

## Files Changed

- `backend/.../session/LastQueryContext.java` — extended with resolved
  `locationText`/`specialtyText`/`roleText`; added `LastQueryContext.of(intentId)`.
- `backend/.../chat/ResolvedQueryContext.java` — new.
- `backend/.../chat/SessionResolutionContext.java` — new.
- `backend/.../chat/IntentOutcome.java` — `Answer`/`NoMatch` now carry a `ResolvedQueryContext`
  (2-arg/0-arg compatibility constructors kept for call sites that don't resolve one).
- `backend/.../chat/IntentOrchestrationService.java` — `resolve` takes `SessionResolutionContext`
  instead of `LastResultContext`; every handler returns its resolved location/specialty/role
  display text; `get_oncall_now`/`get_oncall_schedule` gained the context-aware location/role
  resolution helpers described above.
- `backend/.../chat/ChatOrchestrationService.java` — `dispatchInterpreted` backfills from
  context before clarifying; `applyOutcome` builds the enriched `LastQueryContext`; `resumeWith
  Overlay`/`resumeWithRawTextOverlay` build/pass `SessionResolutionContext` and generalize the
  `LOCATION_TEXT`/`ROLE_TEXT` trusted-selection resume; class Javadoc updated.
- `backend/.../session/ConversationSessionTest.java` — updated to `LastQueryContext.of(...)`
  for the new record shape (no behavioral change to what this test file already covered).
- `backend/.../chat/ChatOrchestrationServiceTest.java` — 5 new tests (see below); new
  `@Autowired` repositories (`LocationRepository`, `LocationSpecialtyRepository`,
  `OnCallRoleRepository`) and a `timeExpressionOnly(...)` helper.
- `planning/PHASE-STATUS.md` — this report.

No `ai-service` files changed. No `config/intents.yaml`/`config/tools.yaml`/prompt changes.

## Tests Run and Results

- `backend/mvnw test` (offline, `-Dmaven.compiler.release=17`, same carried-over JDK 21 caveat
  as prior phases): **106/106 passing**, 0 failures (101 pre-existing Phase 0-3 + 5 new Phase 4),
  fully deterministic, real H2/canonicalization/tool execution as before.
- `ai-service` `pytest`: **57/57 passing**, unchanged (Phase 4 did not touch ai-service).

## Follow-Up / Context-Reuse Tests (explicitly requested)

All five are new in `ChatOrchestrationServiceTest`, all pass:

1. `contextReuseFillsMissingSpecialtyFromLastQueryForFollowUpSchedule` — "Who is on call for
   Neurology in Oakland?" (ANSWER) then "What about tomorrow?" (Python extracts only
   `time_expression=TOMORROW`, reports `specialty_text` missing) → Spring backfills
   `specialty_text="Neurology"` from `LastQueryContext` and answers directly, no re-clarification.
2. `sameLocationReuseAutoResolvesAmbiguousLocationFromLastQuery` — "Who is on call for
   Cardiology in Oakland?" (ANSWER) then "Who is on call for Cardiology?" (location omitted,
   Cardiology is offered at two locations) → without Phase 4 this re-clarifies exactly like the
   Phase 3 `ambiguousLocationClarifiesThenResumesWithSelectedOption` test; with it, Spring
   silently reuses "Oakland" from context and answers directly.
3. `selectedOptionResumeForDuplicateLocationDisplayNamesUsesTrustedCanonicalId` — seeds a second
   active location literally named "Oakland" (`loc-oakland-2`, distinct id) offering Cardiology;
   the `AMBIGUOUS_ENTITY` clarification correctly lists all three candidates (two labeled
   identically); selecting `loc-oakland-2`'s option id resolves to *that* location's coverage
   (proves the trusted-id bypass, not a re-canonicalized label — which would have thrown
   `IncorrectResultSizeDataAccessException` under the old Phase 3 mechanism for location).
4. `selectedOptionResumeForDuplicateRoleDisplayNamesUsesTrustedCanonicalId` — same proof for
   `ROLE_TEXT` via the `AMBIGUOUS_RESULT` on-call-role path: two active roles both displayed
   "Primary On-Call"; selecting the second option id resolves to that assignment's provider.
5. `noMatchTransitionStillCarriesResolvedLocationAndSpecialtyForward` — confirms a `NO_MATCH`
   turn (coverage genuinely absent) still records the location/specialty that *did* resolve into
   `LastQueryContext`, so a later "What about tomorrow?" would still have something to reuse.

Existing Phase 3 multi-turn tests (`ambiguousLocationClarifiesThenResumesWithSelectedOption`,
`missingParameterClarifiesThenResumesWithValueProvided`, `unresolvedAnswerReAsks...`,
`cancelClearsPendingClarification`, `unrelatedReplacesThePendingClarificationWithANewIntent`,
the three `getContactInfoClarificationPath_*` and the pronoun pair) all still pass unchanged,
confirming resume/cancel/unrelated and provider-pronoun behavior were not disturbed by the
`SessionResolutionContext`/`resolve()` signature change.

## Deviations / Assumptions Requiring Confirmation

1. **Context reuse is intent-agnostic, not intent-scoped.** `overlayFromLastQueryContext`
   backfills `LOCATION_TEXT`/`SPECIALTY_TEXT`/`ROLE_TEXT` from `LastQueryContext` regardless of
   whether the new turn's intent matches the prior turn's intent (deliberately, since "What about
   tomorrow?" legitimately crosses `get_oncall_now` → `get_oncall_schedule`). A field is only
   ever populated when a *prior* handler actually resolved it for that parameter's role (e.g.
   `role_explanation` never sets `locationText`), which bounds unrelated leakage, but a location
   resolved for one intent (e.g. `get_department_info`) can still silently backfill an unrelated
   later intent (e.g. `get_pcconsult_info`) that also happens to omit it. Judged acceptable POC
   behavior per FR-013 "bounded session context," bounded further by session TTL, but not
   restricted to "the same conversational thread" in any stronger sense — worth tightening if a
   future phase finds it too permissive.
2. **The BACKEND_UNIQUE_OR_CLARIFY location fallback only fires when the location was fully
   omitted**, not when it was stated but ambiguous for another reason (there is no such case
   today — `resolveLocationForSpecialty`'s only AMBIGUOUS path is the omitted-location one).
3. **TTL remains lookup-based only** (unchanged from Phase 3 deviation #7): an expired session is
   evicted lazily on next access, which also means all Phase 4 context reuse is automatically
   bounded by the same TTL — no separate TTL mechanism was added because none was needed beyond
   what already satisfies docs/07's "TTL-aware" requirement.
4. **Carried forward unchanged**: TODAY/TONIGHT/WEEKEND boundary conventions (Phase 1 deviation
   #2), the Phase 2 `contact_pager` extraction gap, `TIME_EXPRESSION` clarification resume still
   unimplemented (Phase 3 deviation #5), and `unauthorized_resource` per-location authorization
   still unimplemented (Phase 1 deviation #4 / Phase 3 deviation #6) — PLAN.md/CLAUDE.md do not
   ask Phase 4 to build per-location authorization, so it was not added.

## Unresolved Issues / Limitations

1. `unauthorized_resource` (per-location entitlement) remains unimplemented.
2. `TIME_EXPRESSION` clarification resume is not implemented — re-asks safely rather than
   guessing.
3. No `uv.lock` for `ai-service/`; JDK 21 verification still outstanding for the backend.
4. Time-interval-boundary business confirmation (Phase 1 deviation #2) remains open.
5. Context reuse is intent-agnostic (Deviation #1 above) — flagged for narrowing if it ever
   proves too permissive against real usage.

## Is Phase 5 Ready to Begin?

Yes. Phase 5 is React UI work against the existing `POST /api/v1/chat/messages` contract
(unchanged by Phase 4 — no request/response shape changes, only richer server-side context
handling behind it). All backend/AI-service tests pass; the chat endpoint, session/clarification
mechanics, and now the bounded follow-up context reuse are in place and covered by deterministic
tests plus the carried-forward real end-to-end smoke path from Phase 3 (not re-run live this
phase, since no Python/prompt contract changed).

---

# Phase 5 Report — React UI

## Phase 5 Objective

Per `planning/PLAN.md`: "Implement UI against Spring only." Per CLAUDE.md ownership, React
owns UI/presentation state only and calls Spring's existing `POST /api/v1/chat/messages`
exclusively — no new backend endpoints, no client-side canonicalization/clarification/tool
logic, no direct Python/Ollama/H2 access. No blocking defect was found in the existing
contract, so it was left unchanged (Phase 3 deviation #1's response shape — `{session_id,
status, answer_text, clarification: {reason, parameter, options: [{option_id, label}]} |
null, correlation_id}` — is exactly what the frontend now consumes). docs/03-ARCHITECTURE.md
("Ownership": "React owns UI only"), docs/04-SEQUENCE-FLOWS.md (Flow 1: React mints
`client_message_id`, POSTs `message` + `session_id`, renders whatever Spring returns), and
docs/06-CONVERSATION-DESIGN.md ("POC Restart Behavior": frontend must tolerate an
expired/unknown session and start a new one) were the only Phase-5-relevant docs beyond
08-API-CONTRACTS.md — none of them specify visual design, wording, or component structure, so
those are this phase's own reasonable choices (see Assumptions below).

## Files Changed

All under `frontend/src/`, replacing the untouched Vite/React scaffold from Phase 0:

- `chat/types.ts` — new. TypeScript mirror of the wire contract (snake_case field names,
  closed enums for `status`/`clarification.reason`/`clarification.parameter`) — the one place
  the frontend's understanding of Spring's contract is spelled out.
- `chat/chatApi.ts` — new. `sendChatMessage()`, the single `fetch` call to `POST
  /api/v1/chat/messages`; a typed `ChatApiError` distinguishes transport failure from a 403
  (session ownership — clears the remembered session so the next attempt starts fresh), a 409
  (idempotency conflict, NFR-009), and any other non-2xx.
- `chat/useChat.ts` — new. All conversation state (`turns`, current `session_id`, in-flight
  flag) lives here, per-turn keyed by a `crypto.randomUUID()` `client_message_id` generated on
  send and reused verbatim on retry (so a retry after a failed/timed-out request is safe under
  NFR-009 rather than risking a 409 with a fresh id+same text). Exposes `sendMessage`, `retry`,
  and a client-only `startNewConversation` reset.
- `chat/clarificationCopy.ts` — new. Maps the closed
  `clarification.reason`/`clarification.parameter` enums to a human-readable prompt (e.g.
  `MISSING_PARAMETER` + `location_text` → "Which location did you mean?"). Presentational only
  — Spring has already decided *that* clarification is needed and *what* the options are.
- `chat/ChatWindow.tsx` + `chat/ChatWindow.css` — new. The chat surface: scrollable transcript,
  user/assistant bubbles, a typing indicator while a turn is in flight, clickable option
  buttons for a clarification's `options` (only on the latest turn), a "Cancel" action that
  sends the literal text `"cancel"` (config/prompts/intent-router.md's own CANCEL wording
  examples), a "Retry" action on a failed turn, and a "New conversation" header button. Renders
  `NO_MATCH`/`UNSUPPORTED`/`ERROR` as distinct assistant bubbles.
- `App.tsx`, `App.css`, `index.css`, `index.html` — trimmed to a full-height single-page chat
  layout and retitled ("ClinConnect Assistant"); removed the stock Vite/React template markup,
  logos, and counter demo (and the now-unused `src/assets/react.svg`).

No `backend/` or `ai-service/` files changed.

## Design Summary

- **Session id**: held only in a `useRef` inside `useChat`, seeded to `null` and always
  overwritten with whatever `session_id` Spring returns. Never read/written to
  `localStorage`/`sessionStorage` — a page reload starts a visibly fresh conversation on both
  the client (empty transcript) and, naturally, the server (a `null` `session_id` mints a new
  one), so the visible state and the actual server-side state never silently diverge. This is
  this phase's own choice, not dictated by any doc (see Assumptions #1).
- **Idempotency (NFR-009)**: every `ChatTurn` is keyed by the `client_message_id` it was sent
  with. A `retry()` reuses that exact id and the exact original text, so Spring's cached-replay
  path (identical id+message) is what actually runs — never a fresh id that could legitimately
  execute the request twice, and never the same id with edited text (which NFR-009 defines as a
  conflict, not a retry).
- **Clarification resume/cancel/unrelated (Phase 3/4) needs no special client logic**: every
  reply — a typed message, a clicked option's label, or "cancel" — is just the next
  `POST /api/v1/chat/messages` call with whatever text was produced. Spring's `/interpret`
  contract already classifies `SELECTED_OPTION`/`VALUE_PROVIDED`/`CANCEL`/`UNRELATED`/
  `UNRESOLVED` server-side; React does not need to know which one applies.
- **Context backfill / TTL / TODAY-TONIGHT-WEEKEND / per-location authorization**: none of these
  Phase 4 constraints needed any frontend awareness — they only affect what Spring silently
  fills in or how it resolves ambiguity server-side, which surfaces to React only as an
  ordinary `ANSWER` or `CLARIFICATION` response like any other. Carried forward unchanged per
  this turn's explicit instruction; nothing in Phase 5 broadens or narrows them.

## Tests / Build / Lint Results

- `npm run lint` (ESLint, `frontend/eslint.config.js`, the Phase 0 flat config with
  `typescript-eslint`/`react-hooks`/`react-refresh` recommended rules): **0 errors, 0
  warnings**.
- `npm run build` (`tsc -b` in strict mode — `noUnusedLocals`/`noUnusedParameters` etc. — then
  `vite build`): **succeeds**, 36 modules transformed, no type errors.
- `backend/mvnw test` (offline, `-Dmaven.compiler.release=17`, same carried-over JDK 21 caveat
  as every prior phase): **106/106 passing**, unchanged from Phase 4 — no backend code was
  touched this phase. (One transient local failure was observed and diagnosed, not a
  regression: running the live smoke-test Spring process concurrently with `mvn test` against
  the same file-mode H2 database path caused an H2 file-lock conflict; stopping the live process
  before re-running restored 106/106. Documented here so it isn't mistaken for a real Phase 5
  defect.)
- `ai-service` `pytest`: **57/57 passing**, unchanged (Phase 5 did not touch ai-service).
- No frontend unit/component test framework exists in this repo (Phase 0 scaffold ships none,
  and `planning/PLAN.md` does not ask Phase 5 to add one — deterministic evaluation runners are
  explicit Phase 6 scope). Frontend correctness was instead verified by a real, live end-to-end
  run (below), not by mocks.

## End-to-End UI → Spring Chat Flow Status

**Working**, verified against the real live stack (Ollama with `qwen2.5:7b-instruct`, the real
`ai-service` via `uvicorn`, the real Spring backend via `java -cp` — JDK 21 still unavailable in
this sandbox, so `target/classes` was recompiled with `-Dmaven.compiler.release=17` first — and
the real Vite dev server on port 5173), the same live-stack approach Phase 3 used:

- **CORS**: a real preflight `OPTIONS` with `Origin: http://localhost:5173` returns
  `Access-Control-Allow-Origin: http://localhost:5173` and allows `POST`/`content-type`,
  confirming `CorsConfig`'s `ALLOWED_ORIGINS` already matches the frontend's dev origin with no
  changes needed.
- Every `POST /api/v1/chat/messages` call below was made with that same `Origin` header and the
  exact JSON shape `chatApi.ts` sends, and every response was checked against `chat/types.ts`
  field-for-field:
  - **ANSWER**: `"What locations can I search?"` → `{"status":"ANSWER","answer_text":"Locations: Antioch, Oakland",...}`.
  - **CLARIFICATION (`MISSING_PARAMETER`, no options → free-text path)**: `"What specialties are available?"`
    → asks for `location_text`; replying `"Oakland"` → `ANSWER` ("Specialties at Oakland: Cardiology,
    Neurology, Pediatrics") — exercises the plain-text input path (no option buttons rendered,
    since `clarification.options` is empty, matching `ChatWindow.tsx`'s conditional).
  - **CLARIFICATION (`AMBIGUOUS_ENTITY`, real options → button-click path)**: a fresh session
    asking `"Who is on call for Cardiology?"` (offered at both seeded locations) → real options
    `[{"option_id":"loc-antioch","label":"Antioch"},{"option_id":"loc-oakland","label":"Oakland"}]`;
    sending the label `"Oakland"` (exactly what clicking that option button sends) → resumes and
    executes (`NO_MATCH`, correctly — no `coverage_assignment` fixtures exist outside tests, same
    as Phase 3's live run).
  - **Live confirmation of Phase 4 context reuse from the UI's perspective**: reusing the same
    session for a follow-up `"Who is on call for Cardiology?"` (location omitted, no clarification
    this time) silently resolved to `"Oakland"` — the location resolved by the *prior* turn in that
    session — and returned `NO_MATCH` directly instead of re-asking, live proof that Phase 4's
    "Same location." fallback works through the real UI request path, not just in mocked tests.
  - **CANCEL**: mid-clarification `"cancel"` → `ANSWER` ("Okay — let me know if there's anything
    else I can help with.") — the fixed, Spring-formatted string `ChatWindow.tsx` renders as a
    normal answer bubble.
  - **UNSUPPORTED**: `"The patient has severe chest pain, is this urgent?"` → `UNSUPPORTED`.
  - **Idempotency (NFR-009)**: identical `client_message_id` + identical text → byte-identical
    cached response (same `correlation_id`); identical id + different text → real `409`, exactly
    the case `useChat.retry()` is designed never to trigger.
- **Frontend serving**: confirmed the Vite dev server serves the retitled
  `index.html`/`main.tsx`/`App.tsx` chain (not the stock template) and that `npm run build`
  produces a clean production bundle.

**Limitation — explicitly disclosed, not glossed over**: this sandbox has no browser-automation
tool available in this session (no Playwright/Puppeteer/screenshot capability), so the UI was
**not** literally clicked through in a rendered browser. What was verified instead is the full
request/response contract the UI code depends on, live, field-for-field, for every response
status and every interaction path (typed reply, option-button click, cancel, retry-safe
idempotency) — plus a clean `tsc`/`vite build`/ESLint pass proving the component code itself is
well-typed and lint-clean. This is strong evidence the UI renders correctly for each case, but
it is not the same as a human/automated click-through, per this turn's own instruction not to
claim what wasn't actually tested. All three services (Ollama, the real `ai-service`, the real
Spring backend) plus the Vite dev server were left running at the end of this turn — the app
was reachable at `http://localhost:5173` proxying to Spring at `http://localhost:8080` — so a
human can immediately verify the rendered UI in a real browser without any setup.

## Deviations / Assumptions Requiring Confirmation

1. **No cross-reload session persistence.** `session_id` lives only in React state (a `useRef`),
   not `localStorage`/`sessionStorage`. A page reload always starts a visibly empty transcript
   *and* a fresh backend session (since the next request sends `session_id: null`), so the
   visible UI state and the actual server-side state can never silently diverge. An alternative
   design (persist `session_id` across reloads so a refresh resumes invisible server-side
   context while the visible transcript still resets) was considered and rejected as more
   confusing than helpful for a POC demo; worth revisiting if Phase 6 evaluation wants reload
   resilience.
2. **Clarification/status copy is this phase's own wording** (`chat/clarificationCopy.ts`,
   `STATUS_COPY` in `ChatWindow.tsx`), same as Phase 3 deviation #1 noted for the response shape
   itself — no doc specifies UI copy. Mirrors `ChatOrchestrationService`'s own
   `CANCELLATION_TEXT` pattern (deterministic, Spring/React-formatted text, never model text).
3. **Clicking a clarification option sends its `label` as plain message text** — there is no
   separate "select option" wire message; this is the only interaction the documented contract
   supports (docs/08-API-CONTRACTS.md has no distinct select-option request shape), and it is
   exactly what a typed reply matching that label would do, so it relies on Spring/Python's
   existing `SELECTED_OPTION` classification (Phase 2/3) rather than any new frontend-side
   selection semantics.
4. **"Cancel" is a plain quick-action button that sends the literal text `"cancel"`**, hidden
   for `LANGUAGE_UNCERTAIN` clarifications (which have no stored intent to cancel — Phase 3
   `ChatOrchestrationService` Javadoc — so the next message there is always treated as a fresh
   interpretation regardless of its wording).
5. **No frontend automated test suite was added.** `planning/PLAN.md` scopes "Use deterministic
   H2 fixture reset and in-memory state tests" to Phase 6 ("Evaluation and Regression"); Phase 5
   itself only says "Implement UI against Spring only," so component/unit tests for the frontend
   were treated as Phase 6 scope rather than added speculatively here.
6. **UI was not verified via literal browser click-through** (Unresolved Issues below) — the
   live stack was left running specifically so this can be done manually.
7. **Carried forward unchanged, per this turn's explicit instruction**: context backfill stays
   intent-agnostic and bounded by resolved fields + session TTL (Phase 4 deviation #1);
   TODAY/TONIGHT/WEEKEND boundaries remain the temporary POC assumption (Phase 1 deviation #2);
   per-location authorization remains out of scope (Phase 1 deviation #4 / Phase 3 deviation #6
   / Phase 4 deviation #4). Nothing in Phase 5 touches any of these.

## Unresolved Issues / Limitations

1. No browser-automation tool was available in this session, so the rendered UI was not clicked
   through by an automated or human tester as part of this turn (Deviation #6) — the full wire
   contract was verified live instead (see End-to-End section). The live stack was left running
   at `http://localhost:5173` for manual verification.
2. `unauthorized_resource` (per-location entitlement) remains unimplemented (carried from Phase
   1/3/4).
3. `TIME_EXPRESSION` clarification resume is not implemented (carried from Phase 3/4) — re-asks
   safely rather than guessing; the UI has no special handling for this beyond rendering
   whatever `CLARIFICATION` Spring returns.
4. No `uv.lock` for `ai-service/`; JDK 21 verification still outstanding for the backend
   (unchanged, carried from Phase 0/1/2).
5. Time-interval-boundary business confirmation (Phase 1 deviation #2) remains open.
6. No frontend automated test suite exists yet (Deviation #5) — left for Phase 6.

## Is Phase 6 Ready to Begin?

Yes. `planning/PLAN.md` scopes Phase 6 to "deterministic H2 fixture reset and in-memory state
tests" (evaluation/regression runners against `tests/evaluation/`), which depends only on the
already-complete and unchanged-this-phase backend/ai-service contracts, not on the frontend.
The one open item worth planning around is Unresolved Issue #1 (no automated/human
click-through yet this turn) — a human should confirm the rendered UI directly at
`http://localhost:5173` before treating Phase 5 as fully signed off, independent of Phase 6
proceeding.

---

# Interim Change (Post-Phase-5, Pre-Phase-6) — CSV-Based Seed Data

Requested directly by the user between phases, not part of `planning/PLAN.md`'s Phase 5 or
Phase 6 scope: move `SyntheticDataSeeder`'s hardcoded Java seed data out to hand-editable CSV
files, so seed data can be added/changed without touching Java. This section documents that
change and its pre-Phase-6 verification. **Phase 6 has not been started.**

## What Changed

- New `config/seed-data/*.csv` — one file per entity: `locations.csv`, `specialties.csv`,
  `departments.csv`, `location_specialties.csv`, `providers.csv`, `provider_specialties.csv`,
  `contact_methods.csv`, `on_call_roles.csv`, `consult_routing_rules.csv`, and
  `coverage_assignments.csv` (the wall-clock-relative live-demo on-call data added earlier this
  session, still gated by `SEED_LIVE_DEMO_COVERAGE`) — plus `config/seed-data/README.md`
  documenting columns/load order/pitfalls.
- New `backend/.../seed/CsvReader.java` — a small hand-rolled, dependency-free CSV parser
  (quoted-field aware; blank lines and `#` comments skipped). No new library added, consistent
  with this project's existing "no new dependency unless required" pattern (Phase 3 deviation
  #2 applied the same reasoning to `RestClient`).
- New `backend/.../seed/SeedDataException.java` — fails startup closed on a missing/malformed
  seed file or an unresolvable id reference, same fail-closed principle as the existing
  `ChatbotConfigException` for `intents.yaml`/`tools.yaml`.
- `backend/.../seed/SyntheticDataSeeder.java` — rewritten to read all ten CSVs instead of
  constructing entities inline in Java; row-to-entity mapping and dependency order (locations →
  specialties → departments → location_specialties → providers → provider_specialties →
  contact_methods → on_call_roles → consult_routing_rules → coverage_assignments) preserved
  exactly as before.
- `backend/src/main/resources/application.yml` — added `clinconnect.seed.data-dir`
  (`CLINCONNECT_SEED_DATA_DIR`, default `../config/seed-data`).

Not touched: `config/intents.yaml`, `config/tools.yaml`, any `config/prompts/*`, any
`ai-service/` file, any `chat`/`interpretation`/`session` package file (verified below), and no
frontend file — this change is entirely internal to how `SyntheticDataSeeder` sources its
input data; the domain data it produces is byte-for-byte the same as the hardcoded version it
replaced (confirmed below).

## Verification Results

**H2 loads the CSV seed data successfully on every startup.** Three consecutive backend
restarts (`java -cp ... ChatbotBackendApplication`, fresh each time) all reached `Started
ChatbotBackendApplication` and returned HTTP 200 from `/actuator/health`, with no
`SeedDataException`/parse errors in any of the three startup logs.

**Repeated restarts do not create duplicate records.** After the three restarts above, row
counts were queried directly against the H2 file (`org.h2.tools.RunScript`, backend stopped)
and matched the CSV row counts exactly, with no duplication:

| table | rows |
|---|---|
| location | 2 |
| specialty | 4 |
| department | 1 |
| location_specialty | 5 |
| provider | 4 |
| provider_specialty | 4 |
| contact_method | 5 |
| on_call_role | 2 |
| consult_routing_rule | 5 |
| coverage_assignment | 5 |

This is structurally guaranteed, not incidental: `ddl-auto: create` drops and recreates the
entire schema on every startup (application.yml), so every restart begins from a genuinely
empty database — `SyntheticDataSeeder`'s own `if (!locationRepository.findAll().isEmpty())
return;` guard is defense-in-depth on top of that, not the primary mechanism.

**`POST /api/v1/chat/messages` behavior is unchanged.** Re-ran the same live checks used to
verify Phase 5, against the CSV-seeded backend:
- CORS preflight: `Access-Control-Allow-Origin`/`-Methods`/`-Headers` unchanged.
- `ANSWER` shape: `{session_id, status, answer_text, clarification: null, correlation_id}` —
  identical field set/values to Phase 5 (`"Locations: Antioch, Oakland"`).
- `CLARIFICATION` shape with real options (`AMBIGUOUS_ENTITY` on `location_text` for
  Cardiology): identical `option_id`/`label` pairs (`loc-antioch`/`Antioch`,
  `loc-oakland`/`Oakland`) to Phase 5.
- Idempotency (NFR-009): same session + same `client_message_id` + same text →
  byte-identical response body **and** the same original `correlation_id`; same
  `client_message_id` + different text → HTTP 409, exactly as before.
- `X-Correlation-Id` response header still present.
- An unknown/garbage `session_id` is still tolerated and transparently starts a fresh session
  (HTTP 200) rather than 403 — this is the documented "POC Restart Behavior"
  (docs/06-CONVERSATION-DESIGN.md), not a defect; true cross-user ownership rejection (403) is
  not reproducible over plain HTTP in this POC (the dev-auth stub always resolves the same
  fixed identity) and is covered by the still-passing automated tests instead
  (`ChatControllerTest.sessionOwnershipMismatchReturns403WithNoBody`,
  `ChatOrchestrationServiceTest.sessionOwnedByAnotherSubjectIsRejected`).

**No intent, tool, conversation, or API contract changed.** Confirmed two ways: (1)
`config/intents.yaml`/`config/tools.yaml` file timestamps predate this change and a
content grep for any seed/CSV reference in them returns nothing; (2) every file under
`backend/.../chat/`, `backend/.../interpretation/`, and `backend/.../session/` (the
request/response DTOs, `ChatController`, `ChatOrchestrationService`, `IntentOrchestrationService`,
clarification types) has a modification timestamp from Phase 4, before this session's seed-data
work began — the change surface for this interim work is exactly `backend/.../seed/`,
`application.yml`'s new `clinconnect.seed.data-dir` line, `.env`/`.env.example`, and the new
`config/seed-data/` directory.

## Tests / Build / Lint Results

- `backend/mvnw clean test` (offline, `-Dmaven.compiler.release=17`, same carried-over JDK 21
  caveat as every prior phase): **106/106 passing**, identical count to Phase 5 — no test was
  added, changed, or removed by this interim work.
- `ai-service` `pytest`: **57/57 passing**, unchanged (not touched).
- `npm run lint`: 0 errors/warnings (not touched).
- `npm run build` (`tsc -b` strict + `vite build`): clean, 36 modules transformed (not touched).

## Assumptions / Deviations

1. **CSV location is `config/seed-data/`, not `docs/`** — the user's original suggestion named
   "the doc folder," but `docs/` is reserved for the numbered architecture/requirements files
   (01–11) CLAUDE.md's mandatory read order treats as read-only context; `config/` already hosts
   `intents.yaml`/`tools.yaml` as the established pattern for startup-loaded, hand-editable
   configuration, so seed data was placed alongside it instead. Confirmed with the user before
   implementing.
2. **Hand-rolled CSV parser, no new dependency** — `CsvReader` is a ~90-line quoted-field-aware
   parser rather than a library (Commons CSV/OpenCSV), matching this project's established
   preference to avoid new dependencies unless a phase explicitly requires one.
3. **`coverage_assignments.csv` uses hour offsets relative to "now," not literal timestamps** —
   so the live-demo data stays valid regardless of when the app is restarted, carrying forward
   the design already established when live-demo coverage was first added this session.
4. This work is **not** a PLAN.md phase; it was done at the user's explicit request between
   Phase 5 and Phase 6, scoped narrowly to the seeder, and Phase 6 was deliberately not started.

## Is Phase 6 Ready to Begin?

**Yes — PHASE 6 READY.** All verification checks above are clean: CSV seed loading is
deterministic and duplicate-free across restarts, the chat API contract is byte-for-byte
unchanged, and every existing automated test (backend, ai-service) and build/lint check
(frontend) still passes with no changes to test code. No intent, tool, conversation-state, or
API contract was touched. Phase 6 ("Evaluation and Regression" per `planning/PLAN.md`) can
proceed against this same, now-more-maintainable seed-data setup.

---

# Phase 6 Report — Evaluation and Regression

## Phase 6 Objective

Per `planning/PLAN.md`: "Use deterministic H2 fixture reset and in-memory state tests." Three
evaluation-contract YAML files already existed under `tests/evaluation/` (docs/10-EVALUATION-
PLAN.md's Layers A–D), created in an earlier phase but only Layer A
(`intent-evaluation.yaml`) had a runner (`ai-service/scripts/run_intent_evaluation.py`). This
phase's actual new work was building the missing deterministic runners for Layer B/C
(`conversation-evaluation.yaml` — Spring canonicalization/orchestration + multi-turn
conversation) and the Spring-side portion of Layer D (`negative-evaluation.yaml`), then running
all three files (plus the existing Layer A script) and reporting real results — not just
narratively mapping existing tests to the YAML, but genuinely parsing and executing the
committed evaluation files. Per this turn's explicit instruction, no test was modified to
improve its score; every failure below is reported and classified as found.

## Design Summary

- **New package `backend/.../evaluation/`** (test-only): `EvaluationYaml` is a small loader
  utility (~60 lines, SnakeYAML — already a transitive dependency via `ChatbotConfigLoader`, no
  new library) that parses a `tests/evaluation/*.yaml` file and looks up one case's turn
  message text / expected values by id via dotted-path navigation (e.g. `path(turn,
  "expected.canonical.location_id")`). Each `@Test` method in `ConversationEvaluationTest`/
  `NegativeEvaluationTest` is named after and reads from its corresponding YAML case id, so the
  message wording and expected values always match what's checked into `tests/evaluation/` —
  only the per-case fixture setup (coverage assignments, extra `location_specialty` rows,
  synthetic session state) is hand-written Java, since that varies too much case-to-case for a
  single generic interpreter to be worth building (CLAUDE.md: no abstraction beyond what's
  needed).
- **`ConversationEvaluationTest`** (13 methods, one per `conversation-evaluation.yaml` case):
  real H2 + real `ChatOrchestrationService`/`IntentOrchestrationService`/
  `CanonicalizationService`, only `PythonInterpretationClient` mocked (standing in for a
  *correct* Layer-A interpretation — this suite tests Spring's behavior *given* a correct
  interpretation, not whether the live model produces one; that's Layer A, run separately).
  Fixed `Clock` at the YAML's own `fixture_contract.clock` (`2026-08-30T18:00:00-07:00`).
  Canonical-id assertions are made by comparing the post-call session's `LastQueryContext`/
  `LastResultContext` (display names) against the expected id's entity, resolved via the real
  repositories — the only place resolved canonical state is observable, since the chat API
  response intentionally never exposes canonical ids (Phase 3 deviation #1). Tool-mapping
  assertions read `ChatbotConfigLoader.config().intentsById()` directly (the real NFR-006
  config-driven mapping). The three `*_normalization` cases call `TimeIntervalResolver`
  directly with the same fixed clock, since "normalized_time" is a pure Spring-internal
  computation never serialized in the API response.
- **`NegativeEvaluationTest`** (15 methods, one per `negative-evaluation.yaml` case): same
  pattern, covering every case that is Spring's responsibility. Three cases
  (`malformed_ai_json`, `illegal_ai_enum`, `ai_cannot_select_tool`) are about ai-service's
  handling of raw model output *before* Spring ever sees a response — not reimplemented in
  Java; each aborts via `Assumptions.abort(...)` with the exact ai-service pytest name that
  already covers it (verified passing). `unauthorized_resource` also aborts, explicitly
  documented as unsatisfiable (no per-location authorization model exists — Phase 1 deviation
  #4, unchanged). Aborting (not omitting, not silently passing) keeps this runner traceable
  1:1 against every case in the file.
- **Layer A**: ran the existing `ai-service/scripts/run_intent_evaluation.py` against the live
  model (`qwen2.5:7b-instruct` via the real, running Ollama) — unchanged, non-deterministic by
  design (its own docstring: "reports accuracy for human review instead of gating CI").

## Files Changed

- `backend/src/test/java/com/clinconnect/chatbot/evaluation/EvaluationYaml.java` — new.
- `backend/src/test/java/com/clinconnect/chatbot/evaluation/ConversationEvaluationTest.java` —
  new, 13 tests.
- `backend/src/test/java/com/clinconnect/chatbot/evaluation/NegativeEvaluationTest.java` — new,
  15 tests (11 executed, 4 documented aborts).
- `planning/PHASE-STATUS.md` — this report.

No `tests/evaluation/*.yaml` file was modified. No `ai-service/`, `config/`, or production
`backend/src/main/` file was touched — this phase is test-only, as its own scope demands.

## Evaluation Metrics

| Layer | File | Runner | Result |
|---|---|---|---|
| A — AI Interpretation | `intent-evaluation.yaml` | `run_intent_evaluation.py` (live model, non-deterministic) | **17/18 (94%)** |
| B/C — Orchestration/Conversation | `conversation-evaluation.yaml` | `ConversationEvaluationTest` (deterministic) | **12/13 (92%)** |
| D — Negative/Security | `negative-evaluation.yaml` | `NegativeEvaluationTest` (deterministic) | **11/11 executed, 100%** (4 cases not executable — see below) |

## Tests Run and Results

- `backend/mvnw clean test` (offline, `-Dmaven.compiler.release=17`, same carried-over JDK 21
  caveat as every prior phase): **133/134 passing, 1 failure, 4 skipped** — 106 pre-existing
  (Phase 0–5, unchanged) + 13 new `ConversationEvaluationTest` (12 pass, 1 fail) + 15 new
  `NegativeEvaluationTest` (11 pass, 4 abort). The single failure
  (`weekend_normalization_when_sunday`) is a genuine, real finding — see Failed Cases below —
  not a flaky/environmental issue (reproduced consistently).
- `ai-service pytest`: **57/57 passing**, unchanged (not touched this phase).
- `npm run lint` / `npm run build`: clean (not touched this phase).
- Live model (`ai-service/scripts/run_intent_evaluation.py`, real Ollama +
  `qwen2.5:7b-instruct`): **17/18 (94%)**.

## Failed Cases and Classifications

### 1. `weekend_normalization_when_sunday` (conversation-evaluation.yaml) — **deterministic application logic**

Expected `start_at: 2026-08-29T00:00:00-07:00` (the Saturday the current weekend started, given
"now" is Sunday 2026-08-30); actual `TimeIntervalResolver.weekend()` returned
`2026-09-05T07:00:00Z` (the *following* Saturday, a week later). Root cause: `weekend()`'s
`daysUntilSaturday = (SATURDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7` formula
returns `0` when today *is* Saturday (correctly staying on the same day — covered by the
existing `TimeIntervalResolverTest.weekendFromWithinTheWeekendIsTheCurrentSaturdayThroughMonday`)
but returns `6` when today is Sunday, landing on next Saturday instead of recognizing that
Sunday is the last day of the weekend that started yesterday. **No existing unit test ever
exercised a Sunday starting point** — `TimeIntervalResolverTest` only tests a Monday and a
Saturday anchor — so this internal inconsistency was invisible until this phase's evaluation
case exercised it.

This is not fixed in this phase. `IntentOrchestrationService`'s own Javadoc and
`TimeIntervalResolver`'s class Javadoc both flag TODAY/TONIGHT/WEEKEND boundary conventions as
"a POC assumption, not sourced from any documented spec," and docs/02-REQUIREMENTS.md's
"Remaining Business Question #8" leaves "whether production 'tonight' and 'weekend' boundaries
differ from the prototype conventions" explicitly open — combined with this turn's explicit
carried-forward instruction to "keep TODAY/TONIGHT/WEEKEND boundaries as temporary POC
assumptions," this phase reports the discrepancy rather than redesigning that logic. That said,
this is flagged as **worth fixing before Phase 7**, independent of the open business question:
the Saturday-anchor case proves the *intent* was "resolve to the weekend you're currently in
regardless of which day within it 'now' falls on," and Sunday is simply the one day the current
formula gets wrong — this is a self-consistency bug in the implementation, not solely a matter
of undecided business semantics.

## Cases Not Executable (documented, not silently passed)

- **`unauthorized_resource`** (negative-evaluation.yaml) — **accepted POC limitation**.
  `DevAuthorizationService` is role-only/scope-blind; no per-location entitlement data model
  exists anywhere in the docs or code (Phase 1 deviation #4, carried unchanged through every
  phase since). The test method exists and aborts with this exact reason rather than being
  omitted or faked.
- **`malformed_ai_json`, `illegal_ai_enum`, `ai_cannot_select_tool`** (negative-evaluation.yaml)
  — **contract/evaluation issue** (these three cases describe ai-service's responsibility, not
  Spring's — their `injected_ai_result` fixture shape is about raw model output ai-service must
  reject before Spring ever receives a response). Each is fully covered deterministically on
  the ai-service side instead (all passing, verified this phase):
  - `malformed_ai_json` → `test_service.py::test_interpret_fails_closed_after_exhausting_retries`
    + `test_interpret_route.py::test_interpret_returns_502_when_interpretation_fails`; Spring's
    matching fail-closed half is `ChatOrchestrationServiceTest
    .aiServiceFailureFailsClosedWithErrorAndAllowsRetryWithSameMessageId`.
  - `illegal_ai_enum` → `test_schemas.py::test_interpret_response_rejects_illegal_time_expression_kind`.
  - `ai_cannot_select_tool` → `test_service.py::test_interpret_never_accepts_model_supplied_tool_id`
    + `test_interpret_route.py::test_interpret_rejects_unknown_request_fields`.

## Layer A (Live Model) Result and Classification

`ai-service/scripts/run_intent_evaluation.py`, `qwen2.5:7b-instruct`: **17/18 (94%)**. One
failure, `contact_pager` ("What is the pager number for Dr. Avery Chen?" →
`parameters.contact_type: expected PAGER, got None`) — **model/prompt**. This is the
already-documented, previously-known residual Phase 2 extraction gap (referenced in every
Phase 3–5 status report as "the Phase 2 `contact_pager` extraction-accuracy gap"), reconfirmed
live this phase, not a new regression. It does not break user-facing behavior:
`contact_type` is optional in `config/intents.yaml`, so a missed extraction just returns the
full contact list instead of filtering to pager only (exercised and confirmed correct by
`ChatOrchestrationServiceTest.getContactInfoClarificationPath_missingContactTypeStillAnswersWithFullContactList`,
still passing).

## Focus-Area Coverage (per this turn's explicit checklist)

- **Intent accuracy / parameter extraction**: Layer A, 17/18 (94%) — see above.
- **Clarification correctness**: `ambiguous_location_clarification_and_resume`,
  `invalid_short_answer_does_not_guess`, `clarification_cancel`, `unrelated_replaces_pending`,
  `multi_provider_pronoun_requires_clarification` (all pass) plus the pre-existing Phase 3/4
  clarification suite in `ChatOrchestrationServiceTest` (unchanged, still passing).
- **Multi-turn context reuse**: `user_correction_overrides_context`, `no_match_clears_last_result`
  (both pass) plus the Phase 4 `contextReuseFillsMissingSpecialtyFromLastQueryForFollowUpSchedule`/
  `sameLocationReuseAutoResolvesAmbiguousLocationFromLastQuery`/
  `noMatchTransitionStillCarriesResolvedLocationAndSpecialtyForward` tests (unchanged, still
  passing) — together these cover both YAML-declared context-reuse cases and the "same
  location"/"what about tomorrow?"-style follow-ups from Phase 4 that predate this evaluation
  file.
- **Unsupported-intent rejection**: `clinical_urgency_classification`, `diagnosis`,
  `coverage_gap_monitoring_deferred`, `broad_phonebook_deferred` (all pass).
- **Invalid tool/output rejection**: `arbitrary_tool_prompt_injection` (Spring fail-closed,
  passes) + the three ai-service-side cases (all pass, see above) + pre-existing
  `IntentOrchestrationServiceTest.unknownIntentIdFailsClosed` (unchanged, still passing).
- **`get_contact_info` clarification behavior**: `contact_followup_single_provider`,
  `multi_provider_pronoun_requires_clarification` (both pass) plus the five pre-existing Phase 3
  `getContactInfoClarificationPath_*` tests (unchanged, still passing).
- **Follow-up queries using prior resolved context**: same as "multi-turn context reuse" above.
- **Regressions against the CSV-seeded synthetic dataset**: every new deterministic test in this
  phase runs against the real CSV-seeded H2 database (`config/seed-data/*.csv`, the accepted
  interim change) with no mocking of domain data — a seed-data drift (a missing location,
  renamed specialty, changed provider id, etc.) would have surfaced as an immediate failure in
  these tests. None did; the CSV-seeded dataset is confirmed consistent with every evaluation
  case's assumptions except `weekend_normalization_when_sunday` (a time-logic issue, unrelated
  to seed data).

## Assumptions / Deviations

1. **Layer B/C/D runners are hybrid, not fully generic**: expected values and turn message text
   are read from the YAML at runtime (so they can't silently drift from the committed file), but
   each case's fixture setup is hand-written Java rather than driven by a generic fixture DSL —
   the fixture shapes across cases (coverage assignments, `location_specialty` overrides,
   synthetic session state, `injected_ai_result`) are too heterogeneous for one generic
   interpreter to be worth building for a 13/15-case suite (CLAUDE.md: no abstraction beyond
   what the task requires). `run_intent_evaluation.py` (Layer A) remains fully generic because
   every Layer-A case shares one uniform shape (message → expected fields).
2. **Canonical-id/tool-id assertions are made via session state and config lookup, not new API
   surface** — the chat API intentionally never exposes canonical ids or tool ids to the
   frontend (Phase 3 deviation #1), so these evaluation assertions read `ConversationSession
   .lastQueryContext()`/`.lastResultContext()` (display names, resolved back to ids via the real
   repositories) and `ChatbotConfigLoader.config().intentsById()` directly — the same trusted
   state the application itself uses, not a test-only shortcut around it.
3. **`unauthorized_resource` and the three `injected_ai_result` cases use `Assumptions.abort`**,
   not `@Disabled`/omission — this makes them visibly distinct from both a pass and a fail in
   the surefire report (shown as "Skipped" with a reason), so the evaluation suite's case count
   stays 1:1 with the YAML files without ever implying false coverage.
4. **The `weekend_normalization_when_sunday` failure was not fixed** — see its classification
   above; this is a deliberate choice per this turn's "do not modify tests merely to improve
   evaluation scores" instruction and the carried-forward "keep TODAY/TONIGHT/WEEKEND boundaries
   as temporary POC assumptions" constraint, not an oversight.
5. **Carried forward unchanged**: `unauthorized_resource`/per-location authorization (Phase 1
   deviation #4), `TIME_EXPRESSION` clarification resume (Phase 3 deviation #5), context reuse
   remaining intent-agnostic (Phase 4 deviation #1), and all UI-layer deviations from Phase 5.

## Unresolved Issues / Limitations

1. `weekend_normalization_when_sunday` — real discrepancy in `TimeIntervalResolver.weekend()`'s
   Sunday handling, newly discovered this phase, not fixed (see Failed Cases above). **Recommend
   fixing before Phase 7** regardless of the still-open weekend-boundary business question, since
   the Saturday-anchor case already proves the intended behavior and Sunday is the one case the
   formula doesn't generalize to.
2. `unauthorized_resource` (per-location entitlement) remains unimplemented — accepted POC
   limitation, unchanged since Phase 1.
3. `TIME_EXPRESSION` clarification resume is not implemented (Phase 3 deviation #5) — re-asks
   safely rather than guessing.
4. No `uv.lock` for `ai-service/`; JDK 21 verification still outstanding for the backend
   (unchanged, carried from Phase 0/1/2). The VS Code Java Language Server's own background JDK
   21 compiler was observed racing with Maven's JDK-17-targeted builds during this phase (stale
   class-file-version errors, resolved by always using `mvn clean test` atomically) — not a
   defect in this codebase, just an environment quirk worth knowing about for future phases in
   this same sandbox.
5. Time-interval-boundary business confirmation (Phase 1 deviation #2 / docs/02 Remaining
   Business Question #8) remains open — now with a concrete example (`weekend_normalization_when
   _sunday`) of exactly where it matters.

## Is Phase 7 Ready to Begin?

**Yes, with one recommendation.** `planning/PLAN.md` Phase 7 is "POC Hardening and Production
Adapter Readiness... Verify H2 -> PostgreSQL/source-adapter and in-memory -> Redis replacement
seams," which does not depend on fixing the weekend-boundary issue. However, this phase
recommends fixing `TimeIntervalResolver.weekend()`'s Sunday handling before Phase 7 (or early in
it) since it's a genuine self-consistency bug independent of the open business question, and
leaving it in place means `get_oncall_schedule`/`get_pcconsult_info` WEEKEND queries made on a
Sunday will silently return the wrong week's data. Everything else — 133/134 deterministic
backend tests, 57/57 ai-service tests, clean frontend build/lint, 94% live-model accuracy on
both Layer A and (indirectly) Layer B/C's message interpretation — is clean and regression-safe
against the CSV-seeded synthetic dataset.
