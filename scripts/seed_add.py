#!/usr/bin/env python3
"""Batch-add new locations/specialties/providers/coverage to config/seed-data/*.csv
from a single YAML input file, instead of hand-editing every cross-referenced CSV.

Usage (PyYAML lives in the ai-service venv, so run it with that interpreter):

    ai-service/.venv/bin/python scripts/seed_add.py path/to/additions.yaml

See scripts/seed_add.example.yaml for the input format. After it runs, restart
the backend as usual -- config/seed-data/*.csv is read fresh on every startup.

Validates every reference (location/specialty/role) before writing anything,
so a typo aborts with a clear error instead of producing a dangling reference
like the one this script exists to prevent.
"""
import argparse
import csv
import io
import re
import sys
from pathlib import Path

import yaml

SEED_DIR = Path(__file__).resolve().parent.parent / "config" / "seed-data"

VALID_CONTACT_TYPES = {"MOBILE", "OFFICE", "TIE_LINE", "PAGER", "CHART_CHAT", "BACKLINE"}
DEFAULT_TIME_ZONE = "America/Los_Angeles"
DEFAULT_ROLE = "PRIMARY_ONCALL"
DEPARTMENT_NOTE = "Approved for new and established patient consult scheduling."
COVERAGE_STARTS_OFFSET_HOURS = "-48"
COVERAGE_ENDS_OFFSET_HOURS = "216"


def slugify(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.strip().lower()).strip("-")


def provider_id_for(name: str) -> str:
    stripped = re.sub(r"^(Dr\.?|Mr\.?|Ms\.?|Mrs\.?)\s+", "", name.strip())
    return "provider-" + slugify(stripped)


class SeedTable:
    """Reads one seed CSV, preserving its header/comment lines verbatim, and
    supports appending new rows (written immediately to disk, or held back
    with dry_run=True so validation can run before anything touches disk)."""

    def __init__(self, filename: str, key_field: str = "id"):
        self.filename = filename
        self.path = SEED_DIR / filename
        self.key_field = key_field
        text = self.path.read_text()
        self.header = None
        self.rows = []
        for line in text.splitlines():
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            cells = next(csv.reader([line]))
            if self.header is None:
                self.header = cells
                continue
            self.rows.append(dict(zip(self.header, cells)))
        self._pending = []

    def find(self, **match):
        return next((r for r in self.rows if all(r.get(k) == v for k, v in match.items())), None)

    def exists(self, **match) -> bool:
        return self.find(**match) is not None

    def stage(self, row: dict):
        """Queue a row for append; visible to exists()/find() immediately so
        later entries in the same run can reference it, but not written to
        disk until flush()."""
        missing = set(self.header) - set(row)
        if missing:
            raise ValueError(f"{self.filename}: row missing column(s) {missing}: {row}")
        self.rows.append(row)
        self._pending.append(row)

    def flush(self):
        if not self._pending:
            return
        text = self.path.read_text()
        if text and not text.endswith("\n"):
            text += "\n"
        buf = io.StringIO()
        writer = csv.writer(buf, lineterminator="\n")
        for row in self._pending:
            writer.writerow([row[h] for h in self.header])
            print(f"  + {self.filename}: {row[self.key_field]}")
        self.path.write_text(text + buf.getvalue())
        self._pending = []


class SeedAddError(Exception):
    pass


def load_tables():
    return {
        "locations": SeedTable("locations.csv"),
        "specialties": SeedTable("specialties.csv"),
        "departments": SeedTable("departments.csv"),
        "location_specialties": SeedTable("location_specialties.csv", key_field="location_id"),
        "providers": SeedTable("providers.csv"),
        "provider_specialties": SeedTable("provider_specialties.csv", key_field="provider_id"),
        "contact_methods": SeedTable("contact_methods.csv"),
        "on_call_roles": SeedTable("on_call_roles.csv", key_field="code"),
        "consult_routing_rules": SeedTable("consult_routing_rules.csv"),
        "coverage_assignments": SeedTable("coverage_assignments.csv"),
    }


def process_locations(tables, entries):
    for entry in entries or []:
        slug = slugify(entry["slug"])
        loc_id = f"loc-{slug}"
        if tables["locations"].exists(id=loc_id) or tables["locations"].exists(slug=slug):
            print(f"  = locations.csv: {loc_id} already exists, skipping")
            continue
        tables["locations"].stage({
            "id": loc_id,
            "slug": slug,
            "display_name": entry.get("display_name", entry["slug"]),
            "time_zone": entry.get("time_zone", DEFAULT_TIME_ZONE),
            "active": "true",
        })


def process_specialties(tables, entries):
    for entry in entries or []:
        slug = slugify(entry["slug"])
        spec_id = f"spec-{slug}"
        if tables["specialties"].exists(id=spec_id) or tables["specialties"].exists(slug=slug):
            print(f"  = specialties.csv: {spec_id} already exists, skipping")
            continue
        tables["specialties"].stage({
            "id": spec_id,
            "slug": slug,
            "display_name": entry.get("display_name", entry["slug"]),
            "active": "true",
        })


def resolve_location(tables, slug_text: str) -> dict:
    slug = slugify(slug_text)
    loc = tables["locations"].find(slug=slug)
    if loc is None:
        raise SeedAddError(
            f"provider references location '{slug_text}' but no location with that slug exists "
            f"in locations.csv or in this file's own 'locations:' section")
    return loc


def resolve_specialty(tables, slug_text: str) -> dict:
    slug = slugify(slug_text)
    spec = tables["specialties"].find(slug=slug)
    if spec is None:
        raise SeedAddError(
            f"provider references specialty '{slug_text}' but no specialty with that slug exists "
            f"in specialties.csv or in this file's own 'specialties:' section")
    return spec


def process_providers(tables, entries):
    for entry in entries or []:
        name = entry["name"]
        location = resolve_location(tables, entry["location"])
        specialty = resolve_specialty(tables, entry["specialty"])
        role = entry.get("role", DEFAULT_ROLE)
        if not tables["on_call_roles"].exists(code=role):
            raise SeedAddError(f"provider '{name}': role_code '{role}' not found in on_call_roles.csv")

        prov_id = provider_id_for(name)
        is_new_provider = not tables["providers"].exists(id=prov_id)
        if is_new_provider:
            tables["providers"].stage({"id": prov_id, "display_name": name, "active": "true"})
        else:
            print(f"  = providers.csv: {prov_id} already exists, reusing (adding new coverage only)")

        if not tables["provider_specialties"].exists(provider_id=prov_id, specialty_id=specialty["id"]):
            tables["provider_specialties"].stage({"provider_id": prov_id, "specialty_id": specialty["id"]})

        contact = entry.get("contact")
        if contact:
            ctype = contact["type"].upper()
            if ctype not in VALID_CONTACT_TYPES:
                raise SeedAddError(
                    f"provider '{name}': contact type '{contact['type']}' must be one of {sorted(VALID_CONTACT_TYPES)}")
            contact_id = f"contact-{slugify(name.replace('Dr. ', '').replace('Dr.', ''))}-{ctype.lower()}"
            already_has_it = tables["contact_methods"].exists(id=contact_id) or tables["contact_methods"].exists(
                provider_id=prov_id, contact_type=ctype, value=str(contact["value"]))
            if not already_has_it:
                tables["contact_methods"].stage({
                    "id": contact_id,
                    "provider_id": prov_id,
                    "contact_type": ctype,
                    "value": str(contact["value"]),
                    "active": "true",
                })

        if not tables["location_specialties"].exists(location_id=location["id"], specialty_id=specialty["id"]):
            tables["location_specialties"].stage({
                "location_id": location["id"],
                "specialty_id": specialty["id"],
                "active": "true",
            })

        if entry.get("department", True):
            dept_id = f"dept-{location['slug']}-{specialty['slug']}"
            if not tables["departments"].exists(id=dept_id):
                tables["departments"].stage({
                    "id": dept_id,
                    "display_name": f"{location['display_name']} {specialty['display_name']}",
                    "location_id": location["id"],
                    "specialty_id": specialty["id"],
                    "note": DEPARTMENT_NOTE,
                    "active": "true",
                })

        if entry.get("routing_rules", True):
            rule_id = f"rule-{location['slug']}-{specialty['slug']}-daytime"
            if not tables["consult_routing_rules"].exists(id=rule_id):
                tables["consult_routing_rules"].stage({
                    "id": rule_id,
                    "location_id": location["id"],
                    "specialty_id": specialty["id"],
                    "category": "CONSULT_ROUTING",
                    "time_context": "DAYTIME",
                    "declared_urgency": "",
                    "routing_text": (
                        f"Route daytime {specialty['display_name']} consults through the "
                        f"{location['display_name']} {specialty['display_name']} department line."
                    ),
                    "active": "true",
                })
            guidance_id = f"guidance-{location['slug']}-{specialty['slug']}"
            if not tables["consult_routing_rules"].exists(id=guidance_id):
                tables["consult_routing_rules"].stage({
                    "id": guidance_id,
                    "location_id": location["id"],
                    "specialty_id": specialty["id"],
                    "category": "CHART_CHAT_GUIDANCE",
                    "time_context": "",
                    "declared_urgency": "",
                    "routing_text": (
                        f"Use Chart Chat for non-urgent secure messages to {location['display_name']} "
                        f"{specialty['display_name']}; urgent requests must be phoned in."
                    ),
                    "active": "true",
                })

        if entry.get("coverage", True):
            # role suffix keeps the id unique from any existing PRIMARY_ONCALL row when this
            # entry is adding e.g. a BACKUP_ONCALL for the same location+specialty (see the
            # conflict check below for the case that's actually meant to be rejected).
            assignment_id = f"assignment-live-{location['slug']}-{specialty['slug']}-{role.lower().replace('_', '-')}"
            conflict = tables["coverage_assignments"].find(
                location_id=location["id"], specialty_id=specialty["id"], role_code=role)
            if conflict and conflict.get("id") != assignment_id:
                raise SeedAddError(
                    f"provider '{name}': an existing coverage_assignments.csv row "
                    f"'{conflict['id']}' already covers {location['slug']}/{specialty['slug']} with role "
                    f"'{role}' -- two rows with the same location+specialty+role make "
                    f"get_oncall_now return AMBIGUOUS_RESULT (see config/seed-data/README.md). "
                    f"Pick a different role_code or edit/remove the existing row instead.")
            if not tables["coverage_assignments"].exists(id=assignment_id):
                tables["coverage_assignments"].stage({
                    "id": assignment_id,
                    "provider_id": prov_id,
                    "specialty_id": specialty["id"],
                    "location_id": location["id"],
                    "role_code": role,
                    "starts_offset_hours": COVERAGE_STARTS_OFFSET_HOURS,
                    "ends_offset_hours": COVERAGE_ENDS_OFFSET_HOURS,
                })


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("input_file", type=Path, help="YAML file describing what to add (see seed_add.example.yaml)")
    args = parser.parse_args()

    doc = yaml.safe_load(args.input_file.read_text()) or {}
    tables = load_tables()

    try:
        print("Validating and staging changes...")
        process_locations(tables, doc.get("locations"))
        process_specialties(tables, doc.get("specialties"))
        process_providers(tables, doc.get("providers"))
    except SeedAddError as e:
        print(f"\nERROR: {e}", file=sys.stderr)
        print("Nothing was written.", file=sys.stderr)
        sys.exit(1)

    print("\nWriting changes:")
    for table in tables.values():
        table.flush()

    print("\nDone. Restart the backend to pick up the new seed data.")


if __name__ == "__main__":
    main()
