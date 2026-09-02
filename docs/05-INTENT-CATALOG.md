# 05 — Intent Catalog

## Canonical Intent Rules

Canonical intent IDs are fixed and must exactly match `config/intents.yaml`.

The AI service may return only these IDs.

Spring maps each intent to its tool; the AI does not return a tool ID.

## Canonical Parameter Vocabulary

The only interpretation parameters are:

- `location_text: string?`
- `specialty_text: string?`
- `provider_reference: ProviderReference?`
- `role_text: string?`
- `contact_type: ContactType?`
- `time_expression: TimeExpression?`
- `time_context: TimeContext?`
- `declared_urgency: DeclaredUrgency?`

Canonical entity IDs are never returned by the model.

## Intent: get_locations

Purpose:
List active supported locations.

Required parameters:
- none

Tool:
- `get_locations`

## Intent: get_specialties

Purpose:
List active specialties at one location.

Required:
- `location_text`

Tool:
- `get_specialties`

## Intent: get_department_info

Purpose:
Return narrow approved department information for one location/specialty.

Required:
- `location_text`
- `specialty_text`

Tool:
- `get_department_info`

## Intent: get_oncall_now

Purpose:
Return coverage active at the current trusted instant.

Required:
- `specialty_text`

Location:
- `BACKEND_UNIQUE_OR_CLARIFY`

Optional:
- `role_text`

Time behavior:
- omitted time or `CURRENT` means this intent;
- `TODAY`, `TONIGHT`, `TOMORROW`, `WEEKEND`, `SPECIFIC_DATE` do not map here.

Tool:
- `get_oncall_now`

## Intent: get_oncall_schedule

Purpose:
Return coverage for a supported bounded interval.

Required:
- `specialty_text`
- `time_expression`

Location:
- `BACKEND_UNIQUE_OR_CLARIFY`

Allowed time kinds:
- `TODAY`
- `TONIGHT`
- `TOMORROW`
- `WEEKEND`
- `SPECIFIC_DATE`

Tool:
- `get_oncall_schedule`

## Intent: get_contact_info

Purpose:
Return active approved contact methods for one provider.

Required:
- `provider_reference`

Optional:
- `contact_type`

Provider reference kinds:
- `EXPLICIT_TEXT`
- `LAST_RESULT_PROVIDER`

Tool:
- `get_contact_info`

General/bulk phonebook lookup is not included.

## Intent: get_pcconsult_info

Purpose:
Retrieve approved consult-routing information.

Required:
- `location_text`
- `specialty_text`

Optional:
- `time_context`
- `declared_urgency`

If time context is omitted, backend returns all applicable active routing sections.

Tool:
- `get_pcconsult_info`

## Intent: triage_consult

This is a retained legacy canonical ID from the supplied intent map.

It does **not** authorize clinical triage.

Purpose:
Retrieve consult-routing rules when the user explicitly declares urgency.

Required:
- `location_text`
- `specialty_text`
- `declared_urgency`

Tool:
- `get_pcconsult_info`

If urgency would need to be inferred from symptoms, request is unsupported.

## Intent: chart_chat_guidance

Purpose:
Retrieve stored approved Chart Chat/secure-message guidance.

Required:
- `location_text`
- `specialty_text`

Optional:
- `time_context`

Tool:
- `chart_chat_guidance`

## Intent: role_explanation

Purpose:
Return an approved stored role definition.

Required:
- `role_text`

Tool:
- `role_explanation`

## Intent Selection Precedence

When multiple interpretations seem possible:

1. explicit bounded schedule expression (`TODAY`, `TONIGHT`, `TOMORROW`, `WEEKEND`, date) -> `get_oncall_schedule`;
2. explicit current wording or on-call question with omitted time -> `get_oncall_now`;
3. explicit contact request -> `get_contact_info`;
4. explicit list of specialties -> `get_specialties`;
5. department-details request -> `get_department_info`;
6. routing request with explicit urgency language -> `triage_consult`;
7. routing request without required declared urgency -> `get_pcconsult_info`;
8. Chart Chat-specific request -> `chart_chat_guidance`;
9. role-definition request -> `role_explanation`.

Unsupported capabilities must not be coerced into the nearest intent.
