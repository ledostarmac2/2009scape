#!/usr/bin/env python3
"""Add one of each OSRS/RS3-imported item to player bank.

NEVER modify core_data.location — preserve the player's last login position.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAYER = os.path.join(ROOT, "game", "data", "players", "ledostar.json")
ITEMS = list(range(14659, 14662)) + list(range(14676, 14706)) + [14734]

with open(PLAYER, encoding="utf-8") as f:
    data = json.load(f)

# NEVER modify core_data.location — bank scripts must not teleport the player.
saved_location = data["core_data"].get("location")

bank = data["core_data"]["bank"]
existing_ids = {entry["id"] for entry in bank}
max_slot = max(int(e["slot"]) for e in bank) if bank else -1
added = []
for item_id in ITEMS:
    sid = str(item_id)
    if sid in existing_ids:
        continue
    max_slot += 1
    bank.append({"amount": "1", "charge": "1000", "slot": str(max_slot), "id": sid})
    added.append(item_id)

data["core_data"]["location"] = saved_location

with open(PLAYER, "w", encoding="utf-8") as f:
    json.dump(data, f, separators=(",", ":"))

print(f"Added {len(added)} bank items: {added}")
print(f"Skipped (already present): {[i for i in ITEMS if str(i) in existing_ids]}")
