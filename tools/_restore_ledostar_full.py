#!/usr/bin/env python3
"""Restore ledostar bank + inventory from pre-strip git snapshot.

Source: be7ecfd7 (last commit with full bank + inventory before acea0450 strip).
Strips WIP ids 14720-14734 from all containers. Preserves current location.
"""
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAYER = os.path.join(ROOT, "game", "data", "players", "ledostar.json")
SOURCE_COMMIT = "be7ecfd7"
OSRS_IDS = {str(i) for i in list(range(14659, 14662)) + list(range(14676, 14706))}
REMOVE_IDS = {str(i) for i in range(14720, 14735)}


def load_from_git(commit):
    raw = subprocess.check_output(
        ["git", "show", f"{commit}:game/data/players/ledostar.json"], cwd=ROOT
    )
    return json.loads(raw)


def strip_container(items):
    kept = []
    removed = []
    for it in items:
        if not isinstance(it, dict):
            kept.append(it)
            continue
        iid = str(it.get("id", it.get("itemId", "")))
        if iid in REMOVE_IDS:
            removed.append(iid)
        else:
            kept.append(it)
    return kept, removed


def main():
    with open(PLAYER, encoding="utf-8") as f:
        current = json.load(f)
    source = load_from_git(SOURCE_COMMIT)

    saved_location = current["core_data"].get("location")
    core = current["core_data"]
    src_core = source["core_data"]

    # Restore bank and inventory from source snapshot.
    core["bank"] = list(src_core["bank"])
    core["inventory"] = list(src_core["inventory"])

    # Equipment: use source but prefer fire cape over infernal WIP.
    core["equipment"] = list(src_core["equipment"])

    removed_all = []
    for key in ("bank", "inventory", "equipment"):
        core[key], removed = strip_container(core[key])
        removed_all.extend(removed)

    # If infernal was in inventory slot 8, restore fire cape from f0edf764 snapshot.
    inv_ids = {str(e["id"]) for e in core["inventory"]}
    if "6570" not in inv_ids and "14734" in removed_all:
        f0 = load_from_git("f0edf764")
        for it in f0["core_data"]["inventory"]:
            if str(it.get("id")) == "6570":
                entry = dict(it)
                if not any(str(e["id"]) == "6570" for e in core["inventory"]):
                    core["inventory"].append(entry)
                break

    core["location"] = saved_location

    with open(PLAYER, "w", encoding="utf-8") as f:
        json.dump(current, f, separators=(",", ":"))

    bank_osrs = sorted(
        {str(e["id"]) for e in core["bank"] if str(e["id"]) in OSRS_IDS}, key=int
    )
    inv_summary = [
        f"{e['id']}x{e['amount']}@slot{e['slot']}" for e in sorted(core["inventory"], key=lambda x: int(x["slot"]))
    ]
    print(f"source_commit: {SOURCE_COMMIT}")
    print(f"location_preserved: {core['location']}")
    print(f"bank_total: {len(core['bank'])}")
    print(f"bank_osrs_count: {len(bank_osrs)}")
    print(f"bank_osrs_ids: {bank_osrs}")
    print(f"wip_removed: {sorted(set(removed_all), key=int)}")
    print(f"inventory: {inv_summary}")
    eq_summary = [f"{e['id']}@slot{e['slot']}" for e in core["equipment"]]
    print(f"equipment: {eq_summary}")


if __name__ == "__main__":
    main()
