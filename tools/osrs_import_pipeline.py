#!/usr/bin/env python3
"""OSRS -> 2009scape item import pipeline.

THE one-command importer. Reads tools/osrs-import/manifest.json and:
  1. compiles the Java cache tools
  2. extracts item defs + model containers from the local OSRS cache
  3. generates tools/osrs-import/plan.txt (models, cameras, rigs, priorities)
  4. writes models + item definitions into the 2009scape cache (ImportOsrsItemBatch)
  5. merges server-side entries (stats/slot/anims) into game/data/configs/item_configs.json
  6. verifies every imported item decodes from the patched cache
  7. renders inventory-icon previews to tools/osrs-import/previews/
  8. mirrors the cache to the client cache dir

Docs: docs/osrs-item-import.md      Manifest: tools/osrs-import/manifest.json

Usage:
  python tools/osrs_import_pipeline.py run [--no-previews] [--no-mirror] [--only ID[,ID..]]

Level gating: per-item "gated": true in the manifest (re-run the pipeline after flipping).
"""

import argparse
import glob
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")
IMPORT_DIR = os.path.join(TOOLS, "osrs-import")
MANIFEST = os.path.join(IMPORT_DIR, "manifest.json")
PLAN = os.path.join(IMPORT_DIR, "plan.txt")
RECOLOR = os.path.join(IMPORT_DIR, "recolor.json")
EXTRACTED = os.path.join(IMPORT_DIR, "extracted.json")
PREVIEWS = os.path.join(IMPORT_DIR, "previews")
OBJ_OUT = os.path.join(IMPORT_DIR, "obj")
OSRS_CACHE = os.path.join(ROOT, "data", "import", "openrs2-osrs-2565-disk", "cache")
OSRS_ITEMS_JSON = os.path.join(ROOT, "data", "import", "osrs_items.json")
MODEL_CONTAINERS = os.path.join(ROOT, "data", "import", "osrs-model-groups")
GAME_CACHE = os.path.join(ROOT, "game", "data", "cache")
BACKUPS = os.path.join(ROOT, "game", "osrs-import-backups")
ITEM_CONFIGS = os.path.join(ROOT, "game", "data", "configs", "item_configs.json")
CLIENT_CACHE = os.path.join(os.path.expanduser("~"), "cache", "runescape")

JDK = r"C:\Users\btarabocchia\Java\temurin-26.0.1+8\bin"
RUNELITE_JAR = os.path.join(TOOLS, "runelite-src", "cache", "build", "libs", "cache-1.12.29-SNAPSHOT.jar")
ASM_JAR = os.path.join(TOOLS, "lib", "asm-9.7.1.jar")
CLIENT_JAR = os.path.join(ROOT, "game", "client.jar")

# Per-slot defaults: nearest-bone rig reference models (native 2009 worn models) used
# when the OSRS model carries no vskin labels, plus the worn render priority.
# rigMH/rigFH are chathead rig references (head slot only).
SLOT_DEFAULTS = {
    "head":   {"rigM": 21873, "rigF": 21906, "rigMH": 39516, "rigFH": 38751, "priority": 7, "headPriority": 4},
    "cape":   {"rigM": 9638,  "rigF": 9640,  "priority": 0},   # fire cape worn
    "neck":   {"rigM": 9642,  "rigF": 9644,  "priority": 4},   # amulet of fury worn
    "weapon": {"rigM": 5409,  "rigF": 5409,  "priority": 10},  # abyssal whip worn
    "2h":     {"rigM": 5409,  "rigF": 5409,  "priority": 10},
    "shield": {"rigM": 15413, "rigF": 15413, "priority": 10},  # rune defender worn
    "hands":  {"rigM": 13307, "rigF": 13319, "priority": 10},  # barrows gloves worn
    "feet":   {"rigM": 27738, "rigF": 27754, "priority": 0},   # dragon boots worn
    "body":   {"rigM": None,  "rigF": None,  "priority": 0},
    "legs":   {"rigM": None,  "rigF": None,  "priority": 0},
    "ring":   {"priority": 0},
    "ammo":   {"priority": 0},
}

# Vertical offset applied to weapon/shield worn models. Matches the net ferocious-gloves
# rig-centering delta (ComputeWornDy on OSRS 36325 vs Barrows 13307 with extraLift=-5).
HAND_HELD_WORN_DY = 11

EQUIP_SLOT = {"head": 0, "cape": 1, "neck": 2, "weapon": 3, "2h": 3, "body": 4,
              "shield": 5, "legs": 7, "hands": 9, "feet": 10, "ring": 12, "ammo": 13}

SKILL_ID = {"attack": 0, "defence": 1, "strength": 2, "hitpoints": 3, "ranged": 4,
            "prayer": 5, "magic": 6, "agility": 16, "herblore": 15, "thieving": 17,
            "crafting": 12, "fletching": 9, "slayer": 18, "hunter": 21, "mining": 14,
            "smithing": 13, "fishing": 10, "cooking": 7, "firemaking": 11, "woodcutting": 8,
            "runecraft": 20, "farming": 19, "construction": 22, "summoning": 23}

# Server config combat/anim fields cloned from a native template item (configTemplate).
TEMPLATE_KEYS = ["attack_speed", "weapon_interface", "attack_anims", "stand_anim",
                 "stand_turn_anim", "walk_anim", "run_anim", "turn180_anim",
                 "turn90cw_anim", "turn90ccw_anim", "defence_anim", "render_anim",
                 "attack_audios", "equip_audio", "two_handed"]


def log(msg):
    print(f"[pipeline] {msg}", flush=True)


def run(cmd, **kw):
    print("  $ " + " ".join(str(c) for c in cmd), flush=True)
    result = subprocess.run([str(c) for c in cmd], capture_output=True, text=True, **kw)
    if result.stdout:
        print(result.stdout.rstrip())
    if result.returncode != 0:
        print(result.stderr.rstrip(), file=sys.stderr)
        raise SystemExit(f"command failed ({result.returncode}): {cmd[0]}")
    return result.stdout


def gradle_jar(artifact):
    pattern = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2",
                           "files-2.1", "**", f"{artifact}-[0-9]*.jar")
    jars = [j for j in glob.glob(pattern, recursive=True)
            if "sources" not in j and "javadoc" not in j]
    if not jars:
        raise SystemExit(f"missing dependency jar: {artifact} (looked in gradle cache)")
    return sorted(jars)[-1]


def runelite_cp():
    deps = [gradle_jar(a) for a in
            ("slf4j-api", "guava", "gson", "commons-compress", "commons-lang3", "jna")]
    return ";".join([TOOLS, RUNELITE_JAR] + deps)


def base_cp():
    return ";".join([TOOLS, CLIENT_JAR, RUNELITE_JAR, ASM_JAR])


def load_manifest(only_ids):
    with open(MANIFEST, encoding="utf-8") as f:
        manifest = json.load(f)
    items = manifest["items"]
    if only_ids:
        items = [i for i in items if i["newId"] in only_ids]
        if not items:
            raise SystemExit(f"--only matched nothing: {only_ids}")
    seen = set()
    for item in items:
        if item["newId"] in seen:
            raise SystemExit(f"duplicate newId {item['newId']} in manifest")
        seen.add(item["newId"])
    return items


def load_osrsbox():
    with open(OSRS_ITEMS_JSON, encoding="utf-8") as f:
        data = json.load(f)["items"]
    by_id = {}
    for it in (data.values() if isinstance(data, dict) else data):
        if not it.get("duplicate") and it.get("id") not in by_id:
            by_id[it["id"]] = it
    return by_id


def step_compile():
    log("compiling Java tools")
    run([os.path.join(JDK, "javac.exe"), "-cp", base_cp(), "-d", TOOLS,
         os.path.join(TOOLS, "ExtractOsrsItemDefs.java"),
         os.path.join(TOOLS, "ExtractOsrsModels.java"),
         os.path.join(TOOLS, "ImportOsrsItemBatch.java"),
         os.path.join(TOOLS, "ImportOsrsItemModels.java"),
         os.path.join(TOOLS, "PatchItemIconCameras.java"),
         os.path.join(TOOLS, "ParseTextureConfigBundle.java"),
         os.path.join(TOOLS, "RenderClientItemIconSheet.java")])
    run([os.path.join(JDK, "javac.exe"), "-cp", runelite_cp() + ";" + CLIENT_JAR + ";" + TOOLS, "-d", TOOLS,
         os.path.join(TOOLS, "PatchInfernalTexture.java")])


def step_extract_defs(items):
    log("extracting OSRS item definitions")
    out = run([os.path.join(JDK, "java.exe"), "-cp", runelite_cp(), "ExtractOsrsItemDefs",
               OSRS_CACHE] + [str(i["osrsId"]) for i in items])
    fields = None
    defs = {}
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("#"):
            fields = line[1:].split("|")
            continue
        if not line or "|" not in line or fields is None:
            continue
        parts = line.split("|")
        if len(parts) != len(fields):
            continue
        row = dict(zip(fields, parts))
        defs[int(row["osrsId"])] = row
    for item in items:
        if item["osrsId"] not in defs:
            raise SystemExit(f"OSRS def for {item['osrsId']} not extracted")
    with open(EXTRACTED, "w", encoding="utf-8") as f:
        json.dump(defs, f, indent=1)
    return defs


def model_ids(d):
    ids = []
    for key in ("ground", "male0", "male1", "female0", "female1", "maleHead", "femaleHead"):
        mid = int(d[key])
        if mid >= 0:
            ids.append(mid)
    return ids


def step_extract_models(items, defs):
    log("extracting OSRS model containers")
    needed = []
    for item in items:
        d = defs[item["osrsId"]]
        ids = model_ids(d)
        if item.get("skipWorn"):
            ids = [int(d["ground"])]
        for mid in ids:
            if mid not in needed and not os.path.exists(os.path.join(MODEL_CONTAINERS, f"{mid}.container")):
                needed.append(mid)
    if needed:
        run([os.path.join(JDK, "java.exe"), "-cp", runelite_cp(), "ExtractOsrsModels",
             OSRS_CACHE, MODEL_CONTAINERS] + [str(m) for m in needed])
    else:
        log("  all containers already extracted")


def slot_name(box):
    name = box.get("equipment_slot_name")
    if name is None:
        return None
    return name


def icon_angle(value):
    """Client icon camera tables are 2048 entries (0..2047)."""
    return int(value) & 2047


def icon_field(item, key, default):
    """Manifest icon* override, else OSRS extracted value."""
    return item[key] if key in item else default


def step_plan(items, defs, osrsbox):
    log("writing import plan")
    rows = ["#newId|name|ground|male0|male1|female0|female1|maleHead|femaleHead|zoom|xan|yan|zan|xoff|yoff|tradeable|priority|headPriority|rigM0|rigF0|rigMHead|rigFHead|wornDy|equippable|facePriority|configTemplate"]
    for item in items:
        d = defs[item["osrsId"]]
        box = osrsbox.get(item["osrsId"], {})
        slot = slot_name(box)
        slotdef = SLOT_DEFAULTS.get(slot or "", {})
        name = item.get("name") or d["name"]
        skip_worn = item.get("skipWorn", False) or (int(d["male0"]) < 0 and int(d["female0"]) < 0)
        male0 = -1 if skip_worn else int(d["male0"])
        male1 = -1 if skip_worn else int(d["male1"])
        female0 = -1 if skip_worn else int(d["female0"])
        female1 = -1 if skip_worn else int(d["female1"])
        male_head = -1 if skip_worn else int(d["maleHead"])
        female_head = -1 if skip_worn else int(d["femaleHead"])
        priority = item.get("wornPriority", slotdef.get("priority", 0))
        head_priority = slotdef.get("headPriority", 4)
        rig_m = item.get("rigRefMale", slotdef.get("rigM")) or -1
        rig_f = item.get("rigRefFemale", slotdef.get("rigF")) or -1
        rig_mh = slotdef.get("rigMH") or -1
        rig_fh = slotdef.get("rigFH") or -1
        tradeable = box.get("tradeable", True)
        if item.get("wornDy") is not None:
            worn_dy = item["wornDy"]
        elif slot in ("weapon", "shield", "2h"):
            worn_dy = HAND_HELD_WORN_DY
        else:
            worn_dy = 0
        equippable = slot in EQUIP_SLOT
        face_priority = item["facePriority"] if item.get("facePriority") is not None else -1
        config_template = item.get("configTemplate") if item.get("configTemplate") is not None else -1
        rows.append("|".join(str(v) for v in [
            item["newId"], name, d["ground"], male0, male1, female0, female1, male_head, female_head,
            icon_field(item, "iconZoom", d["zoom2d"]),
            icon_angle(icon_field(item, "iconXan", d["xan2d"])),
            icon_angle(icon_field(item, "iconYan", d["yan2d"])),
            icon_angle(icon_field(item, "iconZan", d["zan2d"])),
            icon_field(item, "iconXoff", d["xOffset2d"]),
            icon_field(item, "iconYoff", d["yOffset2d"]),
            str(bool(tradeable)).lower(), priority, head_priority,
            rig_m, rig_f, rig_mh, rig_fh, worn_dy, str(equippable).lower(), face_priority,
            config_template]))
    with open(PLAN, "w", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")


def step_recolor(items):
    overlays = {}
    for item in items:
        entry = {}
        if item.get("recolor"):
            entry["recolor"] = {str(k): v for k, v in item["recolor"].items()}
        if item.get("textureRemap"):
            entry["textureRemap"] = {str(k): v for k, v in item["textureRemap"].items()}
        if entry:
            overlays[str(item["newId"])] = entry
    with open(RECOLOR, "w", encoding="utf-8") as f:
        json.dump(overlays, f, indent=2)
        f.write("\n")


def step_import(items):
    batch_items = [i for i in items if not i.get("legacyGeometry")]
    legacy_items = [i for i in items if i.get("legacyGeometry")]
    if batch_items:
        log("writing models + item definitions into the 2009scape cache (batch)")
        batch_plan = os.path.join(IMPORT_DIR, "plan-batch.txt")
        legacy_ids = {i["newId"] for i in legacy_items}
        with open(PLAN, encoding="utf-8") as src, open(batch_plan, "w", encoding="utf-8") as dst:
            header = src.readline()
            dst.write(header)
            for line in src:
                if line.startswith("#") or not line.strip():
                    dst.write(line)
                    continue
                new_id = int(line.split("|", 1)[0])
                if new_id not in legacy_ids:
                    dst.write(line)
        run([os.path.join(JDK, "java.exe"), "-cp", base_cp(), "ImportOsrsItemBatch",
             GAME_CACHE, BACKUPS, MODEL_CONTAINERS, batch_plan])
    if legacy_items:
        # Identity inventory models + authentic OSRS icon cameras (see ImportOsrsItemModels
        # ITEMS table / extracted.json). Do NOT run PatchItemIconCameras here — it used to
        # overwrite OSRS cameras with stale pre-identity-import tuned angles.
        log("writing legacy geometry items via ImportOsrsItemModels")
        os.makedirs(OBJ_OUT, exist_ok=True)
        cmd = [os.path.join(JDK, "java.exe"), "-cp", base_cp(), "ImportOsrsItemModels",
               GAME_CACHE, BACKUPS, MODEL_CONTAINERS, OBJ_OUT]
        cmd.extend(str(i["newId"]) for i in legacy_items)
        run(cmd)


def bonuses_csv(box):
    b = box.get("bonuses") or {}
    vals = [b.get("attack_stab", 0), b.get("attack_slash", 0), b.get("attack_crush", 0),
            b.get("attack_magic", 0), b.get("attack_ranged", 0),
            b.get("defence_stab", 0), b.get("defence_slash", 0), b.get("defence_crush", 0),
            b.get("defence_magic", 0), b.get("defence_ranged", 0),
            0,                              # summoning defence (no OSRS equivalent)
            b.get("melee_strength", 0),
            b.get("prayer", 0),
            0, 0]
    return ",".join(str(v) for v in vals)


def requirements_string(item, box):
    if item.get("requirements"):
        return item["requirements"]
    reqs = box.get("requirements") or {}
    parts = []
    for skill, level in sorted(reqs.items(), key=lambda kv: SKILL_ID.get(kv[0], 99)):
        if skill not in SKILL_ID:
            log(f"  WARNING: unknown skill '{skill}' in OSRS requirements for {item['newId']}")
            continue
        parts.append("{%d,%d}" % (SKILL_ID[skill], level))
    return "-".join(parts) if parts else None


def step_server_configs(items, defs, osrsbox):
    log("merging server-side item configs")
    with open(ITEM_CONFIGS, encoding="utf-8") as f:
        configs = json.load(f)
    by_id = {c.get("id"): c for c in configs}

    for item in items:
        d = defs[item["osrsId"]]
        box = osrsbox.get(item["osrsId"], {})
        slot = slot_name(box)
        entry = by_id.get(str(item["newId"]))
        created = entry is None
        if created:
            entry = {"id": str(item["newId"])}
            configs.append(entry)
            by_id[entry["id"]] = entry

        entry["name"] = item.get("name") or d["name"]
        examine = item.get("examine") or box.get("examine")
        if examine:
            entry["examine"] = examine
        entry["tradeable"] = "true" if box.get("tradeable", True) else "false"
        entry.setdefault("archery_ticket_price", "0")
        if slot in EQUIP_SLOT:
            entry["equipment_slot"] = str(EQUIP_SLOT[slot])
        if slot == "2h" or box.get("two_handed"):
            entry["two_handed"] = "true"
        if box.get("bonuses"):
            entry["bonuses"] = bonuses_csv(box)

        template_id = item.get("configTemplate")
        if template_id is not None:
            template = by_id.get(str(template_id))
            if template is None:
                raise SystemExit(f"configTemplate {template_id} not found in item_configs.json")
            for key in TEMPLATE_KEYS:
                if key in template:
                    entry[key] = template[key]
        if item.get("attackSpeed") is not None:
            entry["attack_speed"] = str(item["attackSpeed"])

        if item.get("gated"):
            reqs = requirements_string(item, box)
            if reqs:
                entry["requirements"] = reqs
        else:
            entry.pop("requirements", None)
        log(f"  {item['newId']} {entry['name']}: config {'created' if created else 'updated'}"
            + (f" (reqs {entry.get('requirements')})" if entry.get("requirements") else " (no level gate)"))

    with open(ITEM_CONFIGS, "w", encoding="utf-8") as f:
        json.dump(configs, f, indent=2)


def step_patch_infernal_texture(items):
    """Import OSRS infernal lava into native texture slot 59 with full animation."""
    if not any(i.get("newId") == 14734 or i.get("osrsId") == 21295 for i in items):
        return
    log("importing OSRS infernal lava texture 59 (native slot, no fire-cape remap)")
    run([os.path.join(JDK, "java.exe"), "-cp", runelite_cp() + ";" + CLIENT_JAR + ";" + TOOLS,
         "PatchInfernalTexture", GAME_CACHE, OSRS_CACHE])


def step_verify(items):
    log("verifying imported items decode from the patched cache")
    out = run([os.path.join(JDK, "java.exe"), "-cp", base_cp(), "InspectItemModels",
               GAME_CACHE] + [str(i["newId"]) for i in items])
    names = [l for l in out.splitlines() if l.strip().startswith("opcode 2 =")]
    log(f"  decoded {len(names)} item names ({len(items)} expected)")
    if len(names) < len(items):
        raise SystemExit("verification failed: some items did not decode with a name")


def step_previews(items, defs):
    log("rendering inventory icon previews (final cameras from plan/manifest)")
    os.makedirs(PREVIEWS, exist_ok=True)
    for item in items:
        d = defs[item["osrsId"]]
        name = (item.get("name") or d["name"]).replace(" ", "_").replace("'", "")
        out = os.path.join(PREVIEWS, f"{item['newId']}_{name}.png")
        zoom = icon_field(item, "iconZoom", d["zoom2d"])
        xan = icon_angle(icon_field(item, "iconXan", d["xan2d"]))
        yan = icon_angle(icon_field(item, "iconYan", d["yan2d"]))
        zan = icon_angle(icon_field(item, "iconZan", d["zan2d"]))
        xoff = icon_field(item, "iconXoff", d["xOffset2d"])
        yoff = icon_field(item, "iconYoff", d["yOffset2d"])
        run([os.path.join(JDK, "java.exe"), "-cp", base_cp(), "RenderClientItemIconSheet",
             GAME_CACHE, str(item["newId"]), str(zoom), str(xan), str(yan), str(zan),
             out, str(xoff), str(yoff)])
    log(f"  previews in {PREVIEWS}")


def step_mirror():
    log(f"mirroring cache -> {CLIENT_CACHE}")
    os.makedirs(CLIENT_CACHE, exist_ok=True)
    for f in glob.glob(os.path.join(GAME_CACHE, "main_file_cache.*")):
        shutil.copy2(f, CLIENT_CACHE)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    p_run = sub.add_parser("run", help="run the full import pipeline")
    p_run.add_argument("--no-previews", action="store_true")
    p_run.add_argument("--no-mirror", action="store_true")
    p_run.add_argument("--only", help="comma-separated newIds to (re)import")
    args = parser.parse_args()

    only_ids = {int(x) for x in args.only.split(",")} if args.only else None
    items = load_manifest(only_ids)
    osrsbox = load_osrsbox()
    log(f"importing {len(items)} items")

    step_compile()
    defs = step_extract_defs(items)
    step_extract_models(items, defs)
    step_plan(items, defs, osrsbox)
    step_recolor(items)
    step_import(items)
    step_patch_infernal_texture(items)
    step_server_configs(items, defs, osrsbox)
    step_verify(items)
    if not args.no_previews:
        step_previews(items, defs)
    if not args.no_mirror:
        step_mirror()
    log("DONE. Restart the server + client to see the items (or run import-osrs-items.ps1 which does it for you).")


if __name__ == "__main__":
    main()
