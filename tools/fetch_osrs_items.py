#!/usr/bin/env python3
"""
Fetch + normalize a real OSRS item dataset into the import scaffold's schema
(`data/import/osrs_items.json`).

Source of truth (Phase 2 decision):
    osrsreboxed-db (maintained fork of osrsbox-db) `items-complete.json`.
    https://raw.githubusercontent.com/0xNeffarion/osrsreboxed-db/master/docs/items-complete.json

Why this source:
    - Reachable from this environment (the official prices.runescape.wiki host and
      static.runelite.net are not; OpenRS2 is, but a raw cache lacks combat bonuses).
    - Complete for *definition* fields: name, examine, stackable, tradeable, equipment
      slot, combat bonuses, requirements, weapon type and attack speed.
    - Maintained through 2023, so it covers the great majority of post-2013 items.

Known limitations (documented per project rules):
    - Provides NO render/model IDs. That is acceptable: OSRS->2009 model conversion is
      the documented Phase 7 blocker, so every item here is a definition-only candidate.
    - Caps out ~2023; the last couple of years of items would need an OpenRS2 cache pass.
    - Bonuses are preserved as a named dict (full fidelity). Conversion to 2009Scape's
      15-value array is intentionally deferred to the import step, not the fetch step.
"""
from __future__ import annotations

import json
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC_URL = "https://raw.githubusercontent.com/0xNeffarion/osrsreboxed-db/master/docs/items-complete.json"
SRC_CACHE = ROOT / "data" / "import" / "_sources" / "osrsreboxed-items-complete.json"
OUT = ROOT / "data" / "import" / "osrs_items.json"
UA = "2009scape-item-import/1.0 (private fork tooling)"

# osrsreboxed equipment slot string -> 2009Scape numeric equipment slot
SLOT_MAP = {
    "head": 0, "cape": 1, "neck": 2, "ammo": 13, "weapon": 3, "2h": 3,
    "body": 4, "shield": 5, "legs": 7, "hands": 9, "feet": 10, "ring": 12,
}

BONUS_KEYS = (
    "attack_stab", "attack_slash", "attack_crush", "attack_magic", "attack_ranged",
    "defence_stab", "defence_slash", "defence_crush", "defence_magic", "defence_ranged",
    "melee_strength", "ranged_strength", "magic_damage", "prayer",
)


def download() -> None:
    SRC_CACHE.parent.mkdir(parents=True, exist_ok=True)
    if SRC_CACHE.exists() and SRC_CACHE.stat().st_size > 0:
        print(f"using cached source: {SRC_CACHE} ({SRC_CACHE.stat().st_size} bytes)")
        return
    print(f"downloading {SRC_URL} ...")
    req = urllib.request.Request(SRC_URL, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=180) as r, open(SRC_CACHE, "wb") as f:
        f.write(r.read())
    print(f"saved {SRC_CACHE.stat().st_size} bytes")


def normalize(e: dict) -> dict:
    out = {
        "id": e["id"],
        "name": e.get("name") or "",
        "examine": e.get("examine"),
        "tradeable": bool(e.get("tradeable")),
        "stackable": bool(e.get("stackable")),
        "noted": bool(e.get("noted")),
        "members": bool(e.get("members")),
        "value": e.get("cost"),
        "placeholder": bool(e.get("placeholder")),
        "source": "osrsreboxed",
        "source_date": e.get("last_updated"),
    }
    eq = e.get("equipment")
    if eq:
        slot = eq.get("slot")
        out["equipment_slot"] = SLOT_MAP.get(slot)
        out["equipment_slot_name"] = slot
        out["two_handed"] = slot == "2h"
        out["bonuses"] = {k: eq.get(k, 0) for k in BONUS_KEYS}
        out["requirements"] = eq.get("requirements")
    w = e.get("weapon")
    if w:
        out["attack_speed"] = w.get("attack_speed")
        out["weapon_type"] = w.get("weapon_type")
    return out


def main() -> int:
    download()
    raw = json.loads(SRC_CACHE.read_text(encoding="utf-8"))
    src_items = raw.values() if isinstance(raw, dict) else raw
    items = [normalize(e) for e in src_items if isinstance(e, dict) and "id" in e]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps({"_meta": {"source": SRC_URL, "count": len(items)}, "items": items}, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    eq = sum(1 for i in items if "equipment_slot" in i)
    wpn = sum(1 for i in items if "attack_speed" in i)
    print(f"wrote {len(items)} items -> {OUT}")
    print(f"  equipment items: {eq}  |  weapons: {wpn}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
