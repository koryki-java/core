#!/usr/bin/env python3
"""
Generates db.json from model.json for the Snowflake Covid19 database.

Usage:
    python3 generate_db_json.py

Output:
    i18n/db.json
"""

import json
import re
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_JSON = os.path.join(SCRIPT_DIR, "i18n", "en", "model.json")
DB_JSON    = os.path.join(SCRIPT_DIR, "i18n", "db.json")

# ---------------------------------------------------------------------------
# Type-inference rules (applied top-to-bottom, first match wins)
# ---------------------------------------------------------------------------

DATE_NAMES = {
    "date", "published", "entry_date", "info_date",
    "last_update", "restriction_start", "restriction_end",
    "date_implemented", "last_observation_date",
}
DATE_SUFFIXES = ("_date", "_update")

BOOL_SUFFIXES = ("_flag",)

FLOAT_NAMES = {"lat", "long", "latitude", "longitude"}
FLOAT_SUFFIXES = (
    "_pct", "_perc", "_rate", "_utilization",
    "_per_100000", "_per_100k", "_per_1000",
    "_per_hundred", "_per_million", "_per_population",
)
FLOAT_INFIXES = ("_per_",)
FLOAT_PREFIXES = ("percent_",)

INTEGER_NAMES = {
    "cases", "deaths", "population", "beds", "icu_beds", "hospitals",
    "tests", "positive", "negative", "inconclusive", "hospitalized",
    "difference", "total_cases", "new_cases", "cases_new", "deaths_new",
    "total_hospital_beds", "total_chcs", "chc_service_delivery_sites",
    "days_since_last_reported_case", "nr_reporting",
    "total_in", "total_in_icu", "total_in_resp", "total_in_ecmo",
    "new_in", "new_out", "staff_medical", "staff_nursing",
    "total_phase", "active", "confirmed", "recovered",
    "death", "pending", "total", "tested", "deceased",
    "intensive_care", "home_isolation",
}
INTEGER_SUFFIXES = (
    "_cases", "_deaths", "_beds", "_count", "_total", "_covid",
    "_mean", "_lower", "_upper", "_coverage", "_numerator",
    "_denominator", "_confirmed", "_suspected", "_icu",
    "_yes", "_no", "_not_reported",
    "_since_prev_day", "_since_previous_day", "_since_prev_week",
    "_increase", "_cumulative", "currently",
    "_occupied", "_staffed_icu_beds",
)
INTEGER_PREFIXES = (
    "total_", "new_", "people_", "doses_", "daily_",
    "staffed_", "previous_day_", "hospital_onset",
    "inpatient_", "inicucu", "onventilator",
    "allbed_", "icubed_", "invven_", "deaths_", "admis_",
    "newicu_", "totdea_", "bedover_", "icuover_",
    "covid_case_count", "total_covid_",
    "hospitalized", "intensive_care_",
    "home_isolation_", "discharged_", "deceased_",
    "tested_", "total_positive",
)


def infer_type(name: str) -> tuple[str, str]:
    """Return (typeFamily, dialectType) for a column name."""
    n = name.lower()

    # DATE
    if n in DATE_NAMES:
        return "DATE", "DATE"
    if any(n.endswith(s) for s in DATE_SUFFIXES):
        return "DATE", "DATE"

    # BOOLEAN
    if any(n.endswith(s) for s in BOOL_SUFFIXES):
        return "BOOLEAN", "BOOLEAN"

    # FLOAT
    if n in FLOAT_NAMES:
        return "FLOAT", "FLOAT"
    if any(n.endswith(s) for s in FLOAT_SUFFIXES):
        return "FLOAT", "FLOAT"
    if any(inf in n for inf in FLOAT_INFIXES):
        return "FLOAT", "FLOAT"
    if any(n.startswith(p) for p in FLOAT_PREFIXES):
        return "FLOAT", "FLOAT"

    # INTEGER
    if n in INTEGER_NAMES:
        return "INTEGER", "INTEGER"
    if any(n.endswith(s) for s in INTEGER_SUFFIXES):
        return "INTEGER", "INTEGER"
    if any(n.startswith(p) for p in INTEGER_PREFIXES):
        return "INTEGER", "INTEGER"

    # Default
    return "TEXT", "VARCHAR"


def make_column(attr: dict) -> dict:
    family, dialect = infer_type(attr["name"])
    return {
        "name":         attr["name"],
        "label":        attr.get("label"),
        "comment":      attr.get("comment"),
        "description":  attr.get("description"),
        "typeFamily":   family,
        "typeEncoding": None,
        "dialectType":  dialect,
        "nullable":     True,
        "pkPos":        0,
    }


def generate():
    with open(MODEL_JSON, encoding="utf-8") as f:
        model = json.load(f)

    tables = []
    for entity in model.get("entities", []):
        columns = [make_column(a) for a in entity.get("attributes", [])]
        tables.append({
            "name":        entity["name"],
            "label":       entity.get("label"),
            "comment":     entity.get("comment"),
            "description": entity.get("description"),
            "columns":     columns,
        })

    db = {
        "name":        "civid19",
        "label":       None,
        "comment":     None,
        "description": "Covid 19 Database.",
        "tables":      tables,
    }

    with open(DB_JSON, "w", encoding="utf-8") as f:
        json.dump(db, f, indent=2, ensure_ascii=False)

    print(f"Written {len(tables)} tables to {DB_JSON}")
    for t in tables:
        print(f"  {t['name']} ({len(t['columns'])} columns)")


if __name__ == "__main__":
    generate()
