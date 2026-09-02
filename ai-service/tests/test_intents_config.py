from ai_service.intents_config import IntentId, intent_catalog


def test_catalog_loads_all_config_intents():
    catalog = intent_catalog()
    ids = catalog.ids()

    assert "get_oncall_now" in ids
    assert "get_oncall_schedule" in ids
    assert "triage_consult" in ids
    assert len(ids) == 10


def test_intent_id_enum_matches_catalog():
    assert {member.value for member in IntentId} == set(intent_catalog().ids())


def test_oncall_now_required_and_optional_parameters():
    definition = intent_catalog().by_id("get_oncall_now")

    assert definition.required_parameters == ("specialty_text",)
    assert "location_text" in definition.optional_parameters
    assert "role_text" in definition.optional_parameters
    assert definition.allowed_time_kinds == ("CURRENT",)


def test_triage_consult_disallows_clinical_classification():
    definition = intent_catalog().by_id("triage_consult")

    assert definition.clinical_classification_allowed is False
    assert "declared_urgency" in definition.required_parameters


def test_get_contact_info_provider_reference_allowed_kinds():
    definition = intent_catalog().by_id("get_contact_info")

    assert definition.provider_reference_allowed_kinds == ("EXPLICIT_TEXT", "LAST_RESULT_PROVIDER")


def test_unknown_intent_returns_none():
    assert intent_catalog().by_id("does_not_exist") is None
