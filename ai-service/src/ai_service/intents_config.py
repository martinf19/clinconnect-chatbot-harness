"""Loads config/intents.yaml as the single source of truth for canonical
intent IDs and their parameter shape.

Spring (backend/.../config/ChatbotConfigLoader.java) loads the same file for
the same reason: docs/05-INTENT-CATALOG.md requires "Canonical intent IDs
are fixed and must exactly match config/intents.yaml." Duplicating the
intent ID list as a hardcoded Python enum would let the two services drift;
loading it here keeps this service's IntentId enum derived from the same
config Spring validates against.

This loader is intentionally narrow: it reads only what Python's language
interpretation needs (intent ids, descriptions, and the canonical parameter
names each intent may use). Tool IDs, arguments, and authorization scopes in
config/tools.yaml are Spring's concern (NFR-006) and are not read here.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path

import yaml

from ai_service.config import settings
from ai_service.paths import resolve_path


class IntentConfigError(RuntimeError):
    """Raised when config/intents.yaml is missing or structurally invalid.

    A broken intent config is a deployment error and must fail startup
    closed, matching the fail-closed behavior of the Spring-side loader.
    """


@dataclass(frozen=True)
class IntentDefinition:
    id: str
    description: str
    required_parameters: tuple[str, ...]
    optional_parameters: tuple[str, ...]
    allowed_time_kinds: tuple[str, ...] = field(default_factory=tuple)
    provider_reference_allowed_kinds: tuple[str, ...] = field(default_factory=tuple)
    clinical_classification_allowed: bool = True


@dataclass(frozen=True)
class IntentCatalog:
    intents: tuple[IntentDefinition, ...]

    def ids(self) -> tuple[str, ...]:
        return tuple(intent.id for intent in self.intents)

    def by_id(self, intent_id: str) -> IntentDefinition | None:
        for intent in self.intents:
            if intent.id == intent_id:
                return intent
        return None


def _load_yaml_mapping(path: Path) -> dict:
    if not path.is_file():
        raise IntentConfigError(f"Config file not found or not readable: {path}")
    with path.open("r", encoding="utf-8") as handle:
        loaded = yaml.safe_load(handle)
    if not isinstance(loaded, dict):
        raise IntentConfigError(f"Config file did not contain a YAML mapping at top level: {path}")
    return loaded


def load_intent_catalog(configured_path: str | None = None) -> IntentCatalog:
    path = resolve_path(configured_path or settings.intents_config_path)
    doc = _load_yaml_mapping(path)

    intents_node = doc.get("intents")
    if not isinstance(intents_node, list) or not intents_node:
        raise IntentConfigError(f"intents.yaml is missing a non-empty 'intents' list: {path}")

    definitions: list[IntentDefinition] = []
    seen_ids: set[str] = set()
    for entry in intents_node:
        if not isinstance(entry, dict):
            raise IntentConfigError("An entry in intents.yaml 'intents' is not a mapping")

        intent_id = entry.get("id")
        if not isinstance(intent_id, str) or not intent_id:
            raise IntentConfigError("An intent entry in intents.yaml is missing 'id'")
        if intent_id in seen_ids:
            raise IntentConfigError(f"Duplicate intent id in intents.yaml: {intent_id}")
        seen_ids.add(intent_id)

        description = entry.get("description", "")

        parameters = entry.get("parameters") or {}
        if not isinstance(parameters, dict):
            raise IntentConfigError(f"Intent '{intent_id}' has a non-mapping 'parameters' in intents.yaml")

        required: list[str] = []
        optional: list[str] = []
        provider_reference_allowed_kinds: tuple[str, ...] = ()
        for param_name, param_body in parameters.items():
            if not isinstance(param_body, dict):
                raise IntentConfigError(
                    f"Intent '{intent_id}' parameter '{param_name}' is not a mapping in intents.yaml"
                )
            if param_body.get("required"):
                required.append(param_name)
            else:
                optional.append(param_name)
            if param_name == "provider_reference":
                allowed_kinds = param_body.get("allowed_kinds") or []
                provider_reference_allowed_kinds = tuple(str(kind) for kind in allowed_kinds)

        time_policy = entry.get("time_policy") or {}
        allowed_time_kinds: tuple[str, ...] = ()
        if isinstance(time_policy, dict):
            allowed_time_kinds = tuple(str(kind) for kind in (time_policy.get("allowed_kinds") or []))

        clinical_classification_allowed = entry.get("clinical_classification_allowed", True)
        if not isinstance(clinical_classification_allowed, bool):
            clinical_classification_allowed = True

        definitions.append(
            IntentDefinition(
                id=intent_id,
                description=str(description),
                required_parameters=tuple(required),
                optional_parameters=tuple(optional),
                allowed_time_kinds=allowed_time_kinds,
                provider_reference_allowed_kinds=provider_reference_allowed_kinds,
                clinical_classification_allowed=clinical_classification_allowed,
            )
        )

    return IntentCatalog(intents=tuple(definitions))


_catalog = load_intent_catalog()

IntentId = Enum("IntentId", {intent.id.upper(): intent.id for intent in _catalog.intents}, type=str)
"""Dynamically built from config/intents.yaml so this enum can never list an
intent ID Spring does not also recognize (docs/05-INTENT-CATALOG.md)."""


def intent_catalog() -> IntentCatalog:
    return _catalog
