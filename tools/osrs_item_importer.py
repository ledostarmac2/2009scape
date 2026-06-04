#!/usr/bin/env python3
"""
Safe scaffolding for comparing current OSRS item definitions against this
2009Scape fork. This tool intentionally does not write item configs or cache
data during Phase 1-4 work.
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
IMPORT_DIR = ROOT / "data" / "import"
DEFAULT_2009_ITEMS = ROOT / "game" / "data" / "configs" / "item_configs.json"
DEFAULT_OSRS_ITEMS = IMPORT_DIR / "osrs_items.json"
DIFF_JSON = IMPORT_DIR / "osrs_item_diff.json"
DIFF_CSV = IMPORT_DIR / "osrs_item_diff.csv"
ITEM_MAP = IMPORT_DIR / "osrs_to_2009scape_item_id_map.json"
MODEL_MAP = IMPORT_DIR / "osrs_to_2009scape_model_id_map.json"

STATUSES = [
    "pending",
    "definition_imported",
    "model_imported",
    "equipment_configured",
    "tested_inventory",
    "tested_ground",
    "tested_equipped",
    "blocked",
    "skipped",
]


def load_json(path: Path, default: Any = None) -> Any:
    if not path.exists():
        if default is not None:
            return default
        raise FileNotFoundError(path)
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def normalize_items(raw: Any) -> list[dict[str, Any]]:
    if isinstance(raw, dict):
        if "items" in raw and isinstance(raw["items"], list):
            raw_items = raw["items"]
        else:
            raw_items = []
            for key, value in raw.items():
                if isinstance(value, dict):
                    value = dict(value)
                    value.setdefault("id", key)
                    raw_items.append(value)
    elif isinstance(raw, list):
        raw_items = raw
    else:
        raise ValueError("Item data must be a JSON list, dict keyed by id, or dict with an items list.")

    out: list[dict[str, Any]] = []
    for item in raw_items:
        if not isinstance(item, dict):
            continue
        normalized = dict(item)
        try:
            normalized["id"] = int(normalized["id"])
        except Exception:
            continue
        normalized["name"] = str(normalized.get("name") or "").strip()
        out.append(normalized)
    return out


def canonical_name(name: str) -> str:
    return " ".join(name.lower().strip().split())


def is_null_or_placeholder(item: dict[str, Any]) -> bool:
    name = canonical_name(item.get("name", ""))
    if not name or name in {"null", "nul", "null item", "dummy", "unused"}:
        return True
    return name.startswith("null ") or name.startswith("unused ")


def boolish(value: Any) -> bool | None:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        if value.lower() in {"true", "yes", "1"}:
            return True
        if value.lower() in {"false", "no", "0"}:
            return False
    return None


def field_any(item: dict[str, Any], *names: str) -> Any:
    for name in names:
        if name in item:
            return item[name]
    return None


def classify_item(
    osrs: dict[str, Any],
    existing_by_id: dict[int, dict[str, Any]],
    existing_by_name: dict[str, list[dict[str, Any]]],
    osrs_name_counts: Counter[str],
) -> dict[str, Any]:
    osrs_id = int(osrs["id"])
    name = osrs.get("name", "")
    cname = canonical_name(name)
    existing_id = existing_by_id.get(osrs_id)
    existing_named = existing_by_name.get(cname, [])
    equipment_slot = field_any(osrs, "equipment_slot", "equipmentSlot", "slot")
    bonuses = field_any(osrs, "bonuses", "equipment", "combat_stats", "combatStats")
    inventory_model = field_any(osrs, "inventory_model", "inventoryModel", "inventoryModelId", "model")
    ground_model = field_any(osrs, "ground_model", "groundModel", "groundModelId")
    male_model = field_any(osrs, "male_wield_model", "maleWieldModel", "maleModel", "maleModel0")
    female_model = field_any(osrs, "female_wield_model", "femaleWieldModel", "femaleModel", "femaleModel0")
    attack_speed = field_any(osrs, "attack_speed", "attackSpeed")
    weapon_type = field_any(osrs, "weapon_type", "weaponType", "weapon_interface", "weaponInterface")
    tradeable = boolish(field_any(osrs, "tradeable", "isTradeable"))
    stackable = boolish(field_any(osrs, "stackable", "isStackable"))
    noted = "note" in cname or boolish(field_any(osrs, "noted", "isNoted"))

    existing_id_name = canonical_name(existing_id.get("name", "")) if existing_id else ""
    if existing_id is not None and existing_id_name == cname:
        # Same id AND same name -> genuinely the same item.
        category = "already_existing_same_id"
    elif existing_id is not None:
        # Same id but a DIFFERENT 2009Scape item already owns it. The OSRS item is
        # still new; it must get a safe custom id. (Never assume id == identity:
        # OSRS reuses ids that 2009Scape repurposed, e.g. OSRS 13263 Abyssal bludgeon
        # vs 2009Scape 13263 Slayer helmet.)
        category = "id_collision"
    elif existing_named:
        category = "same_name_different_id"
    else:
        category = "osrs_only"

    requires_model = any(v not in (None, "", -1, "-1") for v in [inventory_model, ground_model, male_model, female_model])
    equipment = equipment_slot not in (None, "", -1, "-1") or bonuses is not None
    weapon = attack_speed not in (None, "", -1, "-1") or weapon_type not in (None, "", -1, "-1")

    return {
        "osrs_id": osrs_id,
        "name": name,
        "category": category,
        "existing_2009_id": int(existing_id["id"]) if existing_id else None,
        "existing_2009_name": existing_id.get("name") if existing_id else None,
        "same_name_2009_ids": [int(i["id"]) for i in existing_named],
        "duplicate_name_in_osrs": osrs_name_counts[cname] > 1 if cname else False,
        "placeholder_or_null": is_null_or_placeholder(osrs),
        "noted_or_note_related": bool(noted),
        "tradeable": tradeable,
        "stackable": stackable,
        "equipment_item": equipment,
        "weapon_item": weapon,
        "non_equipment_item": not equipment,
        "quest_or_untradeable_hint": tradeable is False,
        "requires_models": requires_model,
        "definition_only_candidate": not requires_model,
        "inventory_model_id": inventory_model,
        "ground_model_id": ground_model,
        "male_wield_model_id": male_model,
        "female_wield_model_id": female_model,
        "equipment_slot": equipment_slot,
        "weapon_type": weapon_type,
        "attack_speed": attack_speed,
        "has_examine": bool(str(field_any(osrs, "examine", "examineText") or "").strip()),
    }


def compare(args: argparse.Namespace) -> dict[str, Any]:
    IMPORT_DIR.mkdir(parents=True, exist_ok=True)
    existing_items = normalize_items(load_json(args.items_2009))
    existing_by_id = {int(i["id"]): i for i in existing_items}
    existing_by_name: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in existing_items:
        existing_by_name[canonical_name(item.get("name", ""))].append(item)

    source_missing = not args.osrs_items.exists()
    osrs_items = [] if source_missing else normalize_items(load_json(args.osrs_items))
    osrs_name_counts = Counter(canonical_name(i.get("name", "")) for i in osrs_items)
    rows = [classify_item(i, existing_by_id, existing_by_name, osrs_name_counts) for i in osrs_items]

    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "mode": "dry_run",
        "source_missing": source_missing,
        "osrs_source_path": str(args.osrs_items),
        "items_2009_path": str(args.items_2009),
        "items_2009_count": len(existing_items),
        "items_2009_max_id": max(existing_by_id) if existing_by_id else None,
        "osrs_items_count": len(osrs_items),
        "rows_count": len(rows),
        "categories": dict(Counter(r["category"] for r in rows)),
        "blocked": source_missing,
        "blocker": None if not source_missing else (
            "No OSRS item definition dump was found. Place a normalized OSRS dump at "
            f"{args.osrs_items} or pass --osrs-items."
        ),
        "assumptions": [
            "Existing 2009Scape IDs are authoritative and must not be overwritten.",
            "A same-name OSRS item with a different ID requires manual review before mapping.",
            "Model IDs are treated as unsafe to reuse until the model-map file explicitly assigns them.",
            "Definition-only candidates are items with no visible model fields in the OSRS source dump.",
        ],
    }

    write_json(DIFF_JSON, {"summary": summary, "items": rows})
    write_csv(DIFF_CSV, rows)
    ensure_mapping_files(rows)
    return summary


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        "osrs_id",
        "name",
        "category",
        "existing_2009_id",
        "existing_2009_name",
        "same_name_2009_ids",
        "duplicate_name_in_osrs",
        "placeholder_or_null",
        "noted_or_note_related",
        "tradeable",
        "stackable",
        "equipment_item",
        "weapon_item",
        "non_equipment_item",
        "quest_or_untradeable_hint",
        "requires_models",
        "definition_only_candidate",
        "inventory_model_id",
        "ground_model_id",
        "male_wield_model_id",
        "female_wield_model_id",
        "equipment_slot",
        "weapon_type",
        "attack_speed",
        "has_examine",
    ]
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field) for field in fields})


def ensure_mapping_files(rows: list[dict[str, Any]]) -> None:
    item_map = load_json(ITEM_MAP, {"schema": 1, "statuses": STATUSES, "items": []})
    known = {int(i["osrs_item_id"]) for i in item_map.get("items", []) if "osrs_item_id" in i}
    next_custom_id = max([14658] + [int(i.get("item_2009scape_id", 0) or 0) for i in item_map.get("items", [])]) + 1
    for row in rows:
        if row["category"] not in ("osrs_only", "id_collision") or row["placeholder_or_null"] or row["osrs_id"] in known:
            continue
        item_map.setdefault("items", []).append(
            {
                "osrs_item_id": row["osrs_id"],
                "item_2009scape_id": next_custom_id,
                "item_name": row["name"],
                "source_revision_or_date": "pending-osrs-source",
                "import_status": "pending",
                "notes": "Generated by dry-run mapping scaffold; not imported.",
            }
        )
        next_custom_id += 1
    write_json(ITEM_MAP, item_map)

    model_map = load_json(MODEL_MAP, {"schema": 1, "statuses": STATUSES, "models": []})
    write_json(MODEL_MAP, model_map)


def validate(args: argparse.Namespace) -> int:
    errors: list[str] = []
    existing_items = normalize_items(load_json(args.items_2009))
    existing_ids = {int(i["id"]) for i in existing_items}
    item_map = load_json(ITEM_MAP, {"items": []})
    for entry in item_map.get("items", []):
        status = entry.get("import_status")
        mapped_id = entry.get("item_2009scape_id")
        if status not in STATUSES:
            errors.append(f"Invalid status for OSRS item {entry.get('osrs_item_id')}: {status}")
        if status not in {"pending", "blocked", "skipped"} and mapped_id in existing_ids:
            errors.append(f"Imported item would overwrite existing 2009Scape item ID {mapped_id}.")
    if errors:
        for err in errors:
            print(f"ERROR: {err}")
        return 1
    print("Validation passed for current mapping scaffold.")
    return 0


def blocked_import(_args: argparse.Namespace) -> int:
    print("Blocked by design: batch/prototype import is not enabled in Phase 1-4 scaffolding.")
    print("Run 'dry-run' with a verified OSRS source dump first, then implement one prototype import.")
    return 2


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="2009Scape OSRS item import scaffold")
    parser.add_argument("--items-2009", type=Path, default=DEFAULT_2009_ITEMS)
    sub = parser.add_subparsers(dest="command", required=True)

    dry = sub.add_parser("dry-run", help="Compare 2009Scape items to a supplied OSRS item dump.")
    dry.add_argument("--osrs-items", type=Path, default=DEFAULT_OSRS_ITEMS)
    dry.set_defaults(func=compare)

    validate_cmd = sub.add_parser("validate", help="Validate mapping files without importing.")
    validate_cmd.set_defaults(func=validate)

    one_id = sub.add_parser("import-one-id", help="Reserved command; blocked until prototype phase.")
    one_id.add_argument("osrs_id", type=int)
    one_id.set_defaults(func=blocked_import)

    one_name = sub.add_parser("import-one-name", help="Reserved command; blocked until prototype phase.")
    one_name.add_argument("name")
    one_name.set_defaults(func=blocked_import)

    definition = sub.add_parser("import-all-pending-definition-only", help="Reserved command.")
    definition.set_defaults(func=blocked_import)

    equipment = sub.add_parser("import-all-pending-equipment", help="Reserved command.")
    equipment.set_defaults(func=blocked_import)

    rollback = sub.add_parser("rollback-last-import-batch", help="Reserved command.")
    rollback.set_defaults(func=blocked_import)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    result = args.func(args)
    if isinstance(result, int):
        return result
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
