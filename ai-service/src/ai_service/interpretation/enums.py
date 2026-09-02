"""Canonical enums for the interpretation contract.

Values mirror docs/08-API-CONTRACTS.md and docs/05-INTENT-CATALOG.md exactly.
Intent IDs are not hardcoded here: they are loaded from config/intents.yaml
(see intents_config.py) so this service can never drift from the single
canonical source Spring also reads.
"""

from __future__ import annotations

from enum import Enum


class InterpretationStatus(str, Enum):
    INTERPRETED = "INTERPRETED"
    NEEDS_LANGUAGE_CLARIFICATION = "NEEDS_LANGUAGE_CLARIFICATION"
    UNSUPPORTED = "UNSUPPORTED"


class TimeExpressionKind(str, Enum):
    CURRENT = "CURRENT"
    TODAY = "TODAY"
    TONIGHT = "TONIGHT"
    TOMORROW = "TOMORROW"
    WEEKEND = "WEEKEND"
    SPECIFIC_DATE = "SPECIFIC_DATE"


class TimeContext(str, Enum):
    DAYTIME = "DAYTIME"
    AFTER_HOURS = "AFTER_HOURS"
    WEEKEND = "WEEKEND"


class DeclaredUrgency(str, Enum):
    URGENT = "URGENT"
    NON_URGENT = "NON_URGENT"


class ContactType(str, Enum):
    MOBILE = "MOBILE"
    OFFICE = "OFFICE"
    TIE_LINE = "TIE_LINE"
    PAGER = "PAGER"
    CHART_CHAT = "CHART_CHAT"
    BACKLINE = "BACKLINE"


class ProviderReferenceKind(str, Enum):
    EXPLICIT_TEXT = "EXPLICIT_TEXT"
    LAST_RESULT_PROVIDER = "LAST_RESULT_PROVIDER"


class ClarificationParameterName(str, Enum):
    """Allowed values of clarification.parameter per docs/08-API-CONTRACTS.md."""

    LOCATION_TEXT = "location_text"
    SPECIALTY_TEXT = "specialty_text"
    PROVIDER_REFERENCE = "provider_reference"
    ROLE_TEXT = "role_text"
    CONTACT_TYPE = "contact_type"
    TIME_EXPRESSION = "time_expression"
    TIME_CONTEXT = "time_context"
    DECLARED_URGENCY = "declared_urgency"


class ClarificationAnswerKind(str, Enum):
    """Classification of a reply to a pending clarification.

    Spring supplies the pending clarification; Python only classifies the
    new message against it (config/prompts/intent-router.md).
    """

    SELECTED_OPTION = "SELECTED_OPTION"
    VALUE_PROVIDED = "VALUE_PROVIDED"
    CANCEL = "CANCEL"
    UNRELATED = "UNRELATED"
    UNRESOLVED = "UNRESOLVED"
