# Seed Data

Hand-editable synthetic domain data, loaded fresh into H2 on every backend
startup by `SyntheticDataSeeder` (`backend/.../seed/`). Edit a CSV and
restart the backend — no Java changes, no migrations.

Columns are matched by header name (order doesn't matter). Lines that are
blank or start with `#` are comments and are ignored. Wrap a value in double
quotes if it contains a comma (use `""` for a literal quote inside it).

All data must stay synthetic/fictional (NFR-007, CLAUDE.md "Data") — no
real clinician names or PHI.

## Load order (respect foreign-key references)

1. `locations.csv` — `id, slug, display_name, time_zone, active`
2. `specialties.csv` — `id, slug, display_name, active`
3. `departments.csv` — `id, display_name, location_id, specialty_id, note, active`
4. `location_specialties.csv` — `location_id, specialty_id, active`
   (which specialties are offered at which location — drives `get_specialties`
   and the ambiguous-location clarification when more than one location
   offers the requested specialty)
5. `providers.csv` — `id, display_name, active`
6. `provider_specialties.csv` — `provider_id, specialty_id`
7. `contact_methods.csv` — `id, provider_id, contact_type, value, active`
   (`contact_type` is one of `MOBILE, OFFICE, TIE_LINE, PAGER, CHART_CHAT, BACKLINE`)
8. `on_call_roles.csv` — `code, display_name, definition_text, active`
9. `consult_routing_rules.csv` — `id, location_id, specialty_id, category, time_context, declared_urgency, routing_text, active`
   (`category` is `CONSULT_ROUTING` or `CHART_CHAT_GUIDANCE`; `time_context`
   — optional — is `DAYTIME`/`AFTER_HOURS`/`WEEKEND`; `declared_urgency` —
   optional — is `URGENT`/`NON_URGENT`; leave either blank when not applicable)
10. `coverage_assignments.csv` — `id, provider_id, specialty_id, location_id, role_code, starts_offset_hours, ends_offset_hours`
    ("who is on call" data — **only loaded when `SEED_LIVE_DEMO_COVERAGE=true`**,
    see `.env`; never used by the automated test suite, which inserts its own
    fixtures tied to a fixed test clock). Offsets are **hours relative to
    "now" at startup**, not literal timestamps, so a row stays valid no
    matter when the app is restarted. `role_code` must match a code in
    `on_call_roles.csv`.

## Adding a new location/specialty/provider/etc.

1. Add a row with a new, unique `id` (follow the existing `loc-`/`spec-`/
   `provider-`/`contact-`/`assignment-` prefix convention).
2. If it participates in on-call coverage, also add rows to
   `location_specialties.csv`, `provider_specialties.csv`, and (for a live
   demo) `coverage_assignments.csv`.
3. Restart the backend.

## Common pitfalls

- A row referencing an `id` that doesn't exist yet elsewhere (e.g. a
  `coverage_assignments.csv` row naming a `location_id` not in
  `locations.csv`) fails startup with a clear error naming the file/column —
  this is intentional (fail closed, docs/09-SECURITY.md), not a bug.
- Two `coverage_assignments.csv` rows for the same
  `location_id`+`specialty_id`+overlapping window **without** distinct
  `role_code`s make `get_oncall_now`/`get_oncall_schedule` return an
  `AMBIGUOUS_RESULT` clarification instead of a direct answer — keep one row
  per location/specialty/window unless you're deliberately testing that path.
- `active: false` makes a row invisible to all lookups, as if it didn't
  exist — useful for temporarily disabling something without deleting it.
