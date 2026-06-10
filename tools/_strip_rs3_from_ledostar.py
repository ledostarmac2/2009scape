#!/usr/bin/env python3
"""Remove RS3 items (14720-14733) from ledostar bank/inventory/equipment.

NEVER modify core_data.location — preserve the player's last login position.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAYER = os.path.join(ROOT, "game", "data", "players", "ledostar.json")
REMOVE_IDS = {str(i) for i in range(14720, 14734)}


def strip_container(items):
    if not isinstance(items, list):
        return items, 0
    kept = []
    removed = 0
    for it in items:
        if not isinstance(it, dict):
            kept.append(it)
            continue
        iid = str(it.get("id", it.get("itemId", "")))
        if iid in REMOVE_IDS:
            removed += 1
        else:
            kept.append(it)
    return kept, removed


def main():
    with open(PLAYER, encoding="utf-8") as f:
        player = json.load(f)
    total = 0
    core = player.get("core_data", player)
    # NEVER modify core_data.location — bank scripts must not teleport the player.
    saved_location = core.get("location")
    for key in ("inventory", "equipment"):
        if key in core:
            core[key], n = strip_container(core[key])
            total += n
            print(f"{key}: removed {n}")
    if "bank" in core:
        core["bank"], n = strip_container(core["bank"])
        total += n
        print(f"bank: removed {n}")
    core["location"] = saved_location
    with open(PLAYER, "w", encoding="utf-8") as f:
        json.dump(player, f, separators=(",", ":"))
    print(f"total removed: {total}")


if __name__ == "__main__":
    main()
