#!/usr/bin/env python3
"""Server-side finalization for OSRS imported items (14659-14661, 14676-14705, 14734)."""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(ROOT, "tools", "osrs-import", "manifest.json")
ITEM_CONFIGS = os.path.join(ROOT, "game", "data", "configs", "item_configs.json")

# OSRS requirements (skill ids: 0 atk, 1 def, 2 str, 3 hp, 4 range, 5 pray, 6 magic, 14 mining)
FINAL = {
    "14659": {"requirements": "{0,80}-{1,80}", "tradeable": "true", "grand_exchange_price": "50000000", "shop_price": "50000000", "ge_buy_limit": "10"},
    "14660": {"requirements": "{1,70}", "tradeable": "true", "grand_exchange_price": "12000000", "shop_price": "12000000", "ge_buy_limit": "10"},
    "14661": {"requirements": "{1,75}-{2,75}", "tradeable": "true", "grand_exchange_price": "5250000", "shop_price": "5250000", "ge_buy_limit": "10"},
    "14676": {"requirements": "{3,75}", "tradeable": "true", "grand_exchange_price": "15000000", "shop_price": "15000000", "ge_buy_limit": "10"},
    "14677": {"requirements": "{3,75}", "tradeable": "false"},
    "14678": {"requirements": "{3,75}", "tradeable": "true", "grand_exchange_price": "15000000", "shop_price": "15000000", "ge_buy_limit": "10"},
    "14679": {"requirements": "{3,75}", "tradeable": "false"},
    "14680": {"requirements": "{3,75}", "tradeable": "true", "grand_exchange_price": "15000000", "shop_price": "15000000", "ge_buy_limit": "10"},
    "14681": {"requirements": "{3,75}", "tradeable": "false"},
    "14682": {"requirements": "{3,75}", "tradeable": "true", "grand_exchange_price": "15000000", "shop_price": "15000000", "ge_buy_limit": "10"},
    "14683": {"requirements": "{3,75}", "tradeable": "false"},
    "14684": {"requirements": None, "tradeable": "true", "grand_exchange_price": "3000000", "shop_price": "3000000", "ge_buy_limit": "10"},
    "14685": {"requirements": None, "tradeable": "true", "grand_exchange_price": "3000000", "shop_price": "3000000", "ge_buy_limit": "10"},
    "14686": {"requirements": "{0,70}-{1,70}", "tradeable": "false"},
    "14687": {"requirements": "{0,78}", "tradeable": "true", "grand_exchange_price": "25000000", "shop_price": "25000000", "ge_buy_limit": "10"},
    "14688": {"requirements": "{0,70}", "tradeable": "true", "grand_exchange_price": "8000000", "shop_price": "8000000", "ge_buy_limit": "10"},
    "14689": {"requirements": "{0,82}", "tradeable": "true", "grand_exchange_price": "7000000", "shop_price": "7000000", "ge_buy_limit": "10"},
    "14690": {"requirements": "{0,60}-{2,60}", "tradeable": "true", "grand_exchange_price": "1500000", "shop_price": "1500000", "ge_buy_limit": "10"},
    "14691": {"requirements": "{4,70}", "tradeable": "true", "grand_exchange_price": "25000000", "shop_price": "25000000", "ge_buy_limit": "10"},
    "14692": {"requirements": "{4,75}", "tradeable": "false"},
    "14693": {"requirements": "{6,50}", "tradeable": "true", "grand_exchange_price": "1000000", "shop_price": "1000000", "ge_buy_limit": "10"},
    "14694": {"requirements": "{6,70}", "tradeable": "true", "grand_exchange_price": "2500000", "shop_price": "2500000", "ge_buy_limit": "10"},
    "14695": {"requirements": "{1,75}-{4,75}", "tradeable": "true", "grand_exchange_price": "10000000", "shop_price": "10000000", "ge_buy_limit": "10"},
    "14696": {"requirements": "{1,75}-{6,75}", "tradeable": "true", "grand_exchange_price": "3200000", "shop_price": "3200000", "ge_buy_limit": "10"},
    "14697": {"requirements": "{1,75}-{2,75}", "tradeable": "true", "grand_exchange_price": "2000000", "shop_price": "2000000", "ge_buy_limit": "10"},
    "14698": {"requirements": None, "tradeable": "true", "grand_exchange_price": "10000000", "shop_price": "10000000", "ge_buy_limit": "10"},
    "14699": {"requirements": "{6,75}", "tradeable": "false"},
    "14700": {"requirements": "{6,75}", "tradeable": "false"},
    "14701": {"requirements": "{6,75}", "tradeable": "false"},
    "14702": {"requirements": "{0,60}-{1,60}", "tradeable": "false"},
    "14703": {"requirements": "{0,60}-{14,61}", "tradeable": "true", "grand_exchange_price": "4500000", "shop_price": "4500000", "ge_buy_limit": "10"},
    "14704": {"requirements": "{0,75}-{6,75}", "tradeable": "true", "grand_exchange_price": "4000000", "shop_price": "4000000", "ge_buy_limit": "10"},
    "14705": {"requirements": "{0,75}-{6,75}", "tradeable": "true", "grand_exchange_price": "15000000", "shop_price": "15000000", "ge_buy_limit": "10"},
    "14734": {"tradeable": "false"},
}


def main():
    with open(MANIFEST, encoding="utf-8") as f:
        manifest = json.load(f)
    for item in manifest["items"]:
        item["gated"] = True
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    print("manifest: set gated=true on all items")

    with open(ITEM_CONFIGS, encoding="utf-8") as f:
        configs = json.load(f)
    by_id = {c["id"]: c for c in configs if "id" in c}

    for item_id, patch in FINAL.items():
        entry = by_id.get(item_id)
        if entry is None:
            raise SystemExit(f"missing item config {item_id}")
        reqs = patch.get("requirements")
        if reqs:
            entry["requirements"] = reqs
        else:
            entry.pop("requirements", None)
        entry["tradeable"] = patch["tradeable"]
        for key in ("grand_exchange_price", "shop_price", "ge_buy_limit"):
            if key in patch:
                entry[key] = patch[key]
            elif key in entry and patch["tradeable"] == "false" and key.startswith("grand"):
                pass

    with open(ITEM_CONFIGS, "w", encoding="utf-8") as f:
        json.dump(configs, f, indent=2)
        f.write("\n")
    print("item_configs: updated", len(FINAL), "items")
    json.load(open(ITEM_CONFIGS, encoding="utf-8"))
    print("JSON validation: OK")


if __name__ == "__main__":
    main()
