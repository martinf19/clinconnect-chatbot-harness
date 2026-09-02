"""Builds the prompt text sent to Ollama.

The base instructions come verbatim from config/prompts/intent-router.md
(the approved prompt contract). The supported-intent list appended below it
is generated from config/intents.yaml at runtime (intents_config.py) rather
than hardcoded, so the prompt can never advertise an intent ID, required
parameter, or allowed time kind that config/intents.yaml does not actually
define.

config/prompts/clarification.md is not used here: it governs an optional
clarification *phrasing* call that CLAUDE.md/config/prompts/clarification.md
treats as non-baseline (Spring provides its own deterministic fallback
template). Pending-clarification *answer classification* is fully specified
by intent-router.md's own "Pending Clarification" section and implemented
here.
"""

from __future__ import annotations

from functools import lru_cache

from ai_service.config import settings
from ai_service.intents_config import IntentCatalog, intent_catalog
from ai_service.interpretation.schemas import InterpretRequest, PendingClarificationInput
from ai_service.paths import resolve_path


@lru_cache(maxsize=1)
def _load_intent_router_base_prompt() -> str:
    path = resolve_path(settings.intent_router_prompt_path)
    return path.read_text(encoding="utf-8")


def _format_intent_catalog(catalog: IntentCatalog) -> str:
    lines = ["## Supported Intents (from config/intents.yaml — use only these IDs)"]
    for intent in catalog.intents:
        lines.append(f"\n### {intent.id}")
        if intent.description:
            lines.append(intent.description)
        if intent.required_parameters:
            lines.append(f"Required parameters: {', '.join(intent.required_parameters)}")
        if intent.optional_parameters:
            lines.append(f"Optional parameters: {', '.join(intent.optional_parameters)}")
        if intent.allowed_time_kinds:
            lines.append(f"Allowed time_expression kinds: {', '.join(intent.allowed_time_kinds)}")
        if intent.provider_reference_allowed_kinds:
            lines.append(
                f"Allowed provider_reference kinds: {', '.join(intent.provider_reference_allowed_kinds)}"
            )
        if not intent.clinical_classification_allowed:
            lines.append(
                "This intent never authorizes clinical urgency classification; "
                "declared_urgency must be an explicit user statement."
            )
    return "\n".join(lines)


def _format_session_context(request: InterpretRequest) -> str:
    if request.session_context is None or request.session_context.last_result is None:
        return ""
    last_result = request.session_context.last_result
    if not last_result.has_single_provider:
        return ""
    display_name = last_result.provider_display_name or "the previously discussed provider"
    return (
        "\n## Safe Session Context\n"
        f"The prior turn resolved to exactly one provider: {display_name}. "
        "If the user uses a pronoun (\"them\", \"they\", \"him\", \"her\") to refer to that "
        "provider, return provider_reference.kind = LAST_RESULT_PROVIDER (no text)."
    )


_OUTPUT_RULES = """\
## Output Rules

- Set intent_id only when interpretation_status is INTERPRETED. Otherwise leave it null.
- Only set a parameter field when its value is explicitly evidenced in the User Message or
  Safe Session Context. Never guess, infer, or default a value. When unsure, leave the field null.
- declared_urgency must stay null unless the user states "urgent" or "non-urgent" (or an
  equivalent explicit category) themselves. Symptoms/severity/diagnosis language is never
  declared_urgency; that makes the request UNSUPPORTED instead.
- Never invent a location, specialty, provider name, or role that is not literally present in
  the User Message or Safe Session Context.
- Extract every entity the message states, even when other fields are also being extracted:
  do not drop location_text just because specialty_text or time information is also present.
- You do not decide which parameters are missing; that is computed separately. Only extract
  what is stated.
- get_contact_info is for exactly one named or contextually-resolved provider. A request for
  many/all/every provider's contact info ("every doctor", "all providers", "the whole
  directory") is UNSUPPORTED, not get_contact_info: there is no bulk directory lookup.
- Extract contact_type whenever the message names a specific contact channel: "pager"/"page"
  -> PAGER, "mobile"/"cell" -> MOBILE, "office"/"desk" -> OFFICE, "tie line" -> TIE_LINE,
  "backline" -> BACKLINE, "Chart Chat"/"secure message" -> CHART_CHAT. Leave it null only when
  no specific channel is named (e.g. "How can I reach them?").
- Only set provider_reference.kind = LAST_RESULT_PROVIDER when the Safe Session Context section
  above is present and states a single prior provider. Never use LAST_RESULT_PROVIDER otherwise,
  even if the message uses a pronoun. If the message is a contact request that uses a pronoun
  ("them", "they", "him", "her") for one provider but no Safe Session Context section is
  present, still return intent_id = get_contact_info with provider_reference left unset (not
  UNSUPPORTED) — the missing reference is reported separately, not guessed by you.

## Examples

User Message: "Who is on call for Cardiology?"
{"interpretation_status": "INTERPRETED", "intent_id": "get_oncall_now", "specialty_text": "Cardiology", "time_expression_kind": "CURRENT"}

User Message: "Who has Neuro call in Oakland?"
{"interpretation_status": "INTERPRETED", "intent_id": "get_oncall_now", "location_text": "Oakland", "specialty_text": "Neuro", "time_expression_kind": "CURRENT"}

User Message: "Who is covering Neurology in Oakland on September 3, 2026?"
{"interpretation_status": "INTERPRETED", "intent_id": "get_oncall_schedule", "location_text": "Oakland", "specialty_text": "Neurology", "time_expression_kind": "SPECIFIC_DATE", "time_expression_specific_date": "2026-09-03"}

User Message: "The patient has severe chest pain. Is this an urgent Rheumatology consult?"
{"interpretation_status": "UNSUPPORTED"}

User Message: "What is the urgent consult routing for Rheumatology in Antioch?"
{"interpretation_status": "INTERPRETED", "intent_id": "triage_consult", "location_text": "Antioch", "specialty_text": "Rheumatology", "declared_urgency": "URGENT"}

User Message: "Who should I Chart Chat for Rheumatology in Antioch?"
{"interpretation_status": "INTERPRETED", "intent_id": "chart_chat_guidance", "location_text": "Antioch", "specialty_text": "Rheumatology"}

User Message: "What specialties are available?"
{"interpretation_status": "INTERPRETED", "intent_id": "get_specialties"}

User Message: "Give me every doctor's phone number."
{"interpretation_status": "UNSUPPORTED"}

User Message: "What is the pager number for Dr. Avery Chen?"
{"interpretation_status": "INTERPRETED", "intent_id": "get_contact_info", "provider_reference_kind": "EXPLICIT_TEXT", "provider_reference_text": "Dr. Avery Chen", "contact_type": "PAGER"}

The urgent-routing example above is different from the chest-pain example: the user themselves
stated the category "urgent" as a routing filter, they did not ask you to judge a symptom. That
distinction is what separates triage_consult (explicit declared_urgency, always allowed) from
UNSUPPORTED (inferring urgency from symptoms, never allowed).
"""


def build_intent_interpretation_prompt(request: InterpretRequest) -> str:
    base = _load_intent_router_base_prompt()
    catalog_section = _format_intent_catalog(intent_catalog())
    session_section = _format_session_context(request)
    return (
        f"{base}\n\n{catalog_section}\n{session_section}\n\n{_OUTPUT_RULES}\n"
        "## User Message\n"
        f'"{request.message}"\n\n'
        "Respond with JSON only, matching the required schema exactly."
    )


def _format_pending_clarification(pending: PendingClarificationInput) -> str:
    lines = [
        "## Pending Clarification",
        f"reason: {pending.reason}",
        f"parameter: {pending.parameter.value}",
    ]
    if pending.options:
        lines.append("options:")
        for option in pending.options:
            lines.append(f"- option_id: {option.option_id}, label: {option.label}")
    return "\n".join(lines)


_CLARIFICATION_OUTPUT_RULES = """\
## Output Rules

- selected_option_id must be one of the option_id values listed above, copied exactly. Never
  invent an option_id. If no listed option matches, this is not SELECTED_OPTION.
- If the reply plainly cancels ("never mind", "cancel", "forget it"), classify it CANCEL.
- If the reply supplies a plausible raw value for the pending parameter but is not one of the
  listed options (or no options were listed), classify it VALUE_PROVIDED and copy the raw text
  into value_text verbatim. Do not canonicalize or correct it.
- If the reply is clearly a different, unrelated request, classify it UNRELATED and also fill
  in interpretation_status/intent_id/parameters for that new request using the Supported
  Intents above and the same Output Rules as ordinary interpretation (only set fields with
  explicit evidence; never guess).
- If you cannot confidently classify the reply as any of the above, classify it UNRESOLVED and
  set no other fields.
"""


def build_clarification_answer_prompt(request: InterpretRequest) -> str:
    if request.pending_clarification is None:
        raise ValueError("build_clarification_answer_prompt requires pending_clarification")

    base = _load_intent_router_base_prompt()
    catalog_section = _format_intent_catalog(intent_catalog())
    pending_section = _format_pending_clarification(request.pending_clarification)
    return (
        f"{base}\n\n{catalog_section}\n\n{pending_section}\n\n{_CLARIFICATION_OUTPUT_RULES}\n"
        "## User Reply\n"
        f'"{request.message}"\n\n'
        "Respond with JSON only, matching the required schema exactly."
    )
