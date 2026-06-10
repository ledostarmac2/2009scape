#!/usr/bin/env python3
"""RS3 -> 2009scape item import pipeline.

Reads tools/rs3-import/manifest.json and:
  1. downloads required OpenRS2 RS3 caches (if missing)
  2. compiles the Java cache tools
  3. extracts item defs + model containers from per-item RS3 caches
  4. generates tools/rs3-import/plan.txt
  5. writes models + item definitions into the 2009scape cache (ImportOsrsItemBatch)
  6. merges server-side entries into game/data/configs/item_configs.json
  7. verifies every imported item decodes from the patched cache
  8. renders inventory-icon previews
  9. mirrors the cache to the client cache dir

Docs: docs/rs3-item-import.md      Manifest: tools/rs3-import/manifest.json

Usage:
  python tools/rs3_import_pipeline.py run [--no-previews] [--no-mirror] [--only ID[,ID..]]
"""

import argparse
import glob
import json
import os
import shutil
import subprocess
import sys
import zipfile

try:
    import urllib.request
except ImportError:
    urllib = None

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")
IMPORT_DIR = os.path.join(TOOLS, "rs3-import")
MANIFEST = os.path.join(IMPORT_DIR, "manifest.json")
PLAN = os.path.join(IMPORT_DIR, "plan.txt")
EXTRACTED = os.path.join(IMPORT_DIR, "extracted.json")
PREVIEWS = os.path.join(IMPORT_DIR, "previews")
RS3_STATS_JSON = os.path.join(ROOT, "data", "import", "rs3_items_stats.json")
MODEL_CONTAINERS = os.path.join(ROOT, "data", "import", "rs3-model-groups")
GAME_CACHE = os.path.join(ROOT, "game", "data", "cache")
BACKUPS = os.path.join(ROOT, "game", "osrs-import-backups")
ITEM_CONFIGS = os.path.join(ROOT, "game", "data", "configs", "item_configs.json")
CLIENT_CACHE = os.path.join(os.path.expanduser("~"), "cache", "runescape")
IMPORT_DATA = os.path.join(ROOT, "data", "import")

JDK = r"C:\Users\btarabocchia\Java\temurin-26.0.1+8\bin"
RUNELITE_JAR = os.path.join(TOOLS, "runelite-src", "cache", "build", "libs", "cache-1.12.29-SNAPSHOT.jar")
DISPLEE_JAR = os.path.join(TOOLS, "lib", "rs-cache-library-8.1.0.jar")
DISIO_JAR = os.path.join(TOOLS, "lib", "disio-2.3.jar")
KOTLIN_JAR = os.path.join(TOOLS, "lib", "kotlin-stdlib-1.9.22.jar")
LZMA_JAR = os.path.join(TOOLS, "lib", "lzma-java-1.3.jar")
ASM_JAR = os.path.join(TOOLS, "lib", "asm-9.7.1.jar")
CLIENT_JAR = os.path.join(ROOT, "game", "client.jar")

SLOT_DEFAULTS = {
    "head":   {"rigM": 21873, "rigF": 21906, "rigMH": 39516, "rigFH": 38751, "priority": 7, "headPriority": 4},
    "cape":   {"rigM": 9638,  "rigF": 9640,  "priority": 0},
    "neck":   {"rigM": 9642,  "rigF": 9644,  "priority": 4},
    "weapon": {"rigM": 5409,  "rigF": 5409,  "priority": 10},
    "2h":     {"rigM": 5409,  "rigF": 5409,  "priority": 10},
    "shield": {"rigM": 15413, "rigF": 15413, "priority": 10},
    "hands":  {"rigM": 13307, "rigF": 13319, "priority": 10},
    "feet":   {"rigM": 27738, "rigF": 27754, "priority": 0},
    "body":   {"rigM": None,  "rigF": None,  "priority": 0},
    "legs":   {"rigM": None,  "rigF": None,  "priority": 0},
    "ring":   {"priority": 0},
    "ammo":   {"priority": 0},
}

EQUIP_SLOT = {"head": 0, "cape": 1, "neck": 2, "weapon": 3, "2h": 3, "body": 4,
              "shield": 5, "legs": 7, "hands": 9, "feet": 10, "ring": 12, "ammo": 13}

# Net ferocious-gloves rig-centering delta (see tools/ComputeWornDy.java).
HAND_HELD_WORN_DY = 11
CAPE_FACE_PRIORITY = 4

TEMPLATE_KEYS = ["attack_speed", "weapon_interface", "attack_anims", "stand_anim",
                 "stand_turn_anim", "walk_anim", "run_anim", "turn180_anim",
                 "turn90cw_anim", "turn90ccw_anim", "defence_anim", "render_anim",
                 "attack_audios", "equip_audio", "two_handed"]


def log(msg):
    print(f"[rs3-pipeline] {msg}", flush=True)


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


def displee_cp():
    deps = [gradle_jar(a) for a in
            ("slf4j-api", "guava", "gson", "commons-compress", "commons-lang3", "jna")]
    return ";".join([TOOLS, RUNELITE_JAR, DISPLEE_JAR, DISIO_JAR, KOTLIN_JAR, LZMA_JAR] + deps)


def base_cp():
    return ";".join([TOOLS, CLIENT_JAR, RUNELITE_JAR, ASM_JAR])


def rs3_cache_dir(cache_id):
    return os.path.join(IMPORT_DATA, f"openrs2-rs3-{cache_id}-disk", "cache")


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


def load_rs3_stats():
    with open(RS3_STATS_JSON, encoding="utf-8") as f:
        return json.load(f)


# Smallest caches first so PoC items import while large caches download.
CACHE_DOWNLOAD_ORDER = [736, 1475, 839, 302, 554, 286]


def step_download_caches(items):
    needed = {i["rs3CacheId"] for i in items}
    order = [c for c in CACHE_DOWNLOAD_ORDER if c in needed]
    order += sorted(needed - set(order))
    for cache_id in order:
        cache_path = rs3_cache_dir(cache_id)
        marker = os.path.join(cache_path, "main_file_cache.dat2")
        if os.path.exists(marker):
            log(f"cache {cache_id} already present at {cache_path}")
            continue
        url = f"https://archive.openrs2.org/caches/runescape/{cache_id}/disk.zip"
        zip_path = os.path.join(IMPORT_DATA, f"openrs2-rs3-{cache_id}-disk.zip")
        log(f"downloading RS3 cache {cache_id} from {url}")
        os.makedirs(IMPORT_DATA, exist_ok=True)
        if os.path.exists(zip_path) and os.path.getsize(zip_path) < 1024:
            os.remove(zip_path)
        for attempt in range(1, 4):
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "2009scape-rs3-import/1.0"})
                with urllib.request.urlopen(req, timeout=900) as resp, open(zip_path, "wb") as out:
                    shutil.copyfileobj(resp, out)
                if os.path.getsize(zip_path) < 1024:
                    raise OSError(f"zip too small ({os.path.getsize(zip_path)} bytes)")
                with zipfile.ZipFile(zip_path, "r") as zf:
                    bad = zf.testzip()
                    if bad:
                        raise zipfile.BadZipFile(f"corrupt member: {bad}")
                break
            except (OSError, zipfile.BadZipFile) as e:
                if os.path.exists(zip_path):
                    os.remove(zip_path)
                if attempt == 3:
                    raise SystemExit(f"cache {cache_id} download failed after 3 attempts: {e}") from e
                log(f"  cache {cache_id} attempt {attempt} failed ({e}), retrying...")
        extract_root = os.path.join(IMPORT_DATA, f"openrs2-rs3-{cache_id}-disk")
        if os.path.isdir(extract_root):
            shutil.rmtree(extract_root)
        os.makedirs(extract_root, exist_ok=True)
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(extract_root)
        if not os.path.exists(marker):
            raise SystemExit(f"cache {cache_id} download failed: {marker} missing after extract")
        log(f"cache {cache_id} extracted to {extract_root} ({os.path.getsize(zip_path) // 1024 // 1024} MB zip)")


def step_compile():
    log("compiling Java tools")
    run([os.path.join(JDK, "javac.exe"), "-cp", displee_cp(),
         os.path.join(TOOLS, "ExtractRs3ItemDefs.java")])
    run([os.path.join(JDK, "javac.exe"), "-cp", runelite_cp(),
         os.path.join(TOOLS, "ExtractRs3Models.java")])
    tolerant = os.path.join(TOOLS, "runelite-patch", "net", "runelite", "cache",
                            "definitions", "loaders", "TolerantModelLoader.java")
    run([os.path.join(JDK, "javac.exe"), "-cp", runelite_cp(), "-d", TOOLS, tolerant])
    run([os.path.join(JDK, "javac.exe"), "-cp", base_cp() + ";" + TOOLS, "-d", TOOLS,
         os.path.join(TOOLS, "ImportOsrsItemBatch.java")])


def step_extract_defs(items):
    log("extracting RS3 item definitions")
    stats = load_rs3_stats()
    by_cache = {}
    for item in items:
        by_cache.setdefault(item["rs3CacheId"], []).append(item)
    fields = None
    defs = {}
    for cache_id, cache_items in sorted(by_cache.items()):
        cache_path = rs3_cache_dir(cache_id)
        tokens = []
        for i in cache_items:
            name = i.get("name") or stats.get(str(i["rs3Id"]), {}).get("name", "")
            tokens.append(f"{i['rs3Id']}|{name}")
        out = run([os.path.join(JDK, "java.exe"), "-cp", displee_cp(), "ExtractRs3ItemDefs",
                   cache_path] + tokens)
        for line in out.splitlines():
            line = line.strip()
            if line.startswith("#"):
                fields = line[1:].replace("rs3Id", "rs3Id").split("|")
                continue
            if not line or "|" not in line or fields is None:
                continue
            parts = line.split("|")
            if len(parts) != len(fields):
                continue
            row = dict(zip(fields, parts))
            defs[int(row["rs3Id"])] = row
    overrides_path = os.path.join(IMPORT_DIR, "def-overrides.json")
    if os.path.exists(overrides_path):
        with open(overrides_path, encoding="utf-8") as f:
            overrides = json.load(f)
        for rs3_id, patch in overrides.items():
            key = int(rs3_id)
            if key in defs:
                defs[key].update(patch)
                log(f"  applied def override for rs3Id {key}: {patch}")
    for item in items:
        if item["rs3Id"] not in defs:
            raise SystemExit(f"RS3 def for {item['rs3Id']} not extracted")
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
    log("extracting RS3 model containers")
    by_cache_models = {}
    for item in items:
        d = defs[str(item["rs3Id"])] if str(item["rs3Id"]) in defs else defs[item["rs3Id"]]
        ids = model_ids(d)
        if item.get("skipWorn"):
            ids = [int(d["ground"])]
        elif item.get("wornTemplate") is not None:
            ids = [int(d["ground"])]
        for mid in ids:
            if mid not in by_cache_models.setdefault(item["rs3CacheId"], []):
                if not os.path.exists(os.path.join(MODEL_CONTAINERS, f"{mid}.container")):
                    by_cache_models[item["rs3CacheId"]].append(mid)
    for cache_id, needed in sorted(by_cache_models.items()):
        if not needed:
            continue
        cache_path = rs3_cache_dir(cache_id)
        run([os.path.join(JDK, "java.exe"), "-cp", runelite_cp(), "ExtractRs3Models",
             cache_path, MODEL_CONTAINERS] + [str(m) for m in needed])
    if not any(by_cache_models.values()):
        log("  all containers already extracted")


def slot_name(stats):
    return stats.get("equipment_slot_name")


def icon_angle(value):
    """Client icon camera tables are 2048 entries (0..2047)."""
    return int(value) & 2047


def icon_offset(value):
    """RS3 exports large signed offsets that push icons off-screen in the 2009 client."""
    signed = int(value)
    if signed > 32767:
        signed -= 65536
    if signed < -128 or signed > 128:
        return 0
    return signed


def icon_field(item, key, default):
    """Manifest icon* override, else RS3 extracted value."""
    return item[key] if key in item else default


def resolved_icon(item, d):
    return {
        "zoom": int(icon_field(item, "iconZoom", d["zoom2d"])),
        "xan": icon_angle(icon_field(item, "iconXan", d["xan2d"])),
        "yan": icon_angle(icon_field(item, "iconYan", d["yan2d"])),
        "zan": icon_angle(icon_field(item, "iconZan", d["zan2d"])),
        "xoff": icon_offset(icon_field(item, "iconXoff", d["xOffset2d"])),
        "yoff": icon_offset(icon_field(item, "iconYoff", d["yOffset2d"])),
    }


def read_native_worn_models(item_id):
    """Read male0/female0 worn model ids from an existing 2009 cache item def."""
    out = run([os.path.join(JDK, "java.exe"), "-cp", base_cp(), "InspectItemModels",
               GAME_CACHE, str(item_id)])
    male0 = female0 = -1
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("opcode 23 = "):
            male0 = int(line.split("=", 1)[1].strip())
        elif line.startswith("opcode 25 = "):
            female0 = int(line.split("=", 1)[1].strip())
    if male0 < 0:
        raise SystemExit(f"wornTemplate {item_id}: no opcode 23 worn model in cache")
    if female0 < 0:
        female0 = male0
    return male0, female0


def step_plan(items, defs, rs3_stats):
    log("writing import plan")
    rows = ["#newId|name|ground|male0|male1|female0|female1|maleHead|femaleHead|zoom|xan|yan|zan|xoff|yoff|tradeable|priority|headPriority|rigM0|rigF0|rigMHead|rigFHead|wornDy|equippable|facePriority|configTemplate"]
    for item in items:
        key = item["rs3Id"]
        d = defs.get(key) or defs.get(str(key))
        stats = rs3_stats.get(str(key), {})
        slot = slot_name(stats)
        slotdef = SLOT_DEFAULTS.get(slot or "", {})
        name = item.get("name") or stats.get("name") or d["name"]
        skip_worn = item.get("skipWorn", False) or (int(d["male0"]) < 0 and int(d["female0"]) < 0)
        worn_template = item.get("wornTemplate")
        if worn_template is not None and not skip_worn:
            tmpl_m0, tmpl_f0 = read_native_worn_models(int(worn_template))
            log(f"  {item['newId']}: wornTemplate {worn_template} -> male0={tmpl_m0} female0={tmpl_f0}")
            male0 = tmpl_m0
            male1 = -1
            female0 = tmpl_f0
            female1 = -1
            male_head = -1
            female_head = -1
        else:
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
        tradeable = stats.get("tradeable", True)
        if item.get("wornDy") is not None:
            worn_dy = item["wornDy"]
        elif slot in ("weapon", "shield", "2h"):
            worn_dy = HAND_HELD_WORN_DY
        else:
            worn_dy = 0
        equippable = slot in EQUIP_SLOT
        if item.get("facePriority") is not None:
            face_priority = item["facePriority"]
        elif slot == "cape":
            face_priority = CAPE_FACE_PRIORITY
        else:
            face_priority = -1
        config_template = item.get("configTemplate") if item.get("configTemplate") is not None else -1
        cam = resolved_icon(item, d)
        ground = item.get("inventoryModel") or d["ground"]
        rows.append("|".join(str(v) for v in [
            item["newId"], name, ground, male0, male1, female0, female1, male_head, female_head,
            cam["zoom"], cam["xan"], cam["yan"], cam["zan"], cam["xoff"], cam["yoff"],
            str(bool(tradeable)).lower(), priority, head_priority,
            rig_m, rig_f, rig_mh, rig_fh, worn_dy, str(equippable).lower(), face_priority,
            config_template]))
    with open(PLAN, "w", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")


def step_import():
    log("writing models + item definitions into the 2009scape cache")
    run([os.path.join(JDK, "java.exe"), "-cp", base_cp(), "ImportOsrsItemBatch",
         GAME_CACHE, BACKUPS, MODEL_CONTAINERS, PLAN])


def bonuses_csv(stats):
    b = stats.get("bonuses") or {}
    vals = [b.get("attack_stab", 0), b.get("attack_slash", 0), b.get("attack_crush", 0),
            b.get("attack_magic", 0), b.get("attack_ranged", 0),
            b.get("defence_stab", 0), b.get("defence_slash", 0), b.get("defence_crush", 0),
            b.get("defence_magic", 0), b.get("defence_ranged", 0),
            0,
            b.get("melee_strength", 0),
            b.get("prayer", 0),
            0, 0]
    return ",".join(str(v) for v in vals)


def step_server_configs(items, defs, rs3_stats):
    log("merging server-side item configs")
    with open(ITEM_CONFIGS, encoding="utf-8") as f:
        configs = json.load(f)
    by_id = {c.get("id"): c for c in configs}

    for item in items:
        key = item["rs3Id"]
        d = defs.get(key) or defs.get(str(key))
        stats = rs3_stats.get(str(key), {})
        slot = slot_name(stats)
        entry = by_id.get(str(item["newId"]))
        created = entry is None
        if created:
            entry = {"id": str(item["newId"])}
            configs.append(entry)
            by_id[entry["id"]] = entry

        entry["name"] = item.get("name") or stats.get("name") or d["name"]
        examine = item.get("examine") or stats.get("examine")
        if examine:
            entry["examine"] = examine
        entry["tradeable"] = "true" if stats.get("tradeable", True) else "false"
        entry.setdefault("archery_ticket_price", "0")
        if slot in EQUIP_SLOT:
            entry["equipment_slot"] = str(EQUIP_SLOT[slot])
        if slot == "2h" or stats.get("two_handed"):
            entry["two_handed"] = "true"
        if stats.get("bonuses"):
            entry["bonuses"] = bonuses_csv(stats)

        template_id = item.get("configTemplate")
        if template_id is not None:
            template = by_id.get(str(template_id))
            if template is None:
                raise SystemExit(f"configTemplate {template_id} not found in item_configs.json")
            for tkey in TEMPLATE_KEYS:
                if tkey in template:
                    entry[tkey] = template[tkey]
        if item.get("attackSpeed") is not None:
            entry["attack_speed"] = str(item["attackSpeed"])

        if item.get("gated"):
            reqs = item.get("requirements")
            if reqs:
                entry["requirements"] = reqs
        else:
            entry.pop("requirements", None)
        log(f"  {item['newId']} {entry['name']}: config {'created' if created else 'updated'}")

    with open(ITEM_CONFIGS, "w", encoding="utf-8") as f:
        json.dump(configs, f, indent=2)


def step_verify(items):
    log("verifying imported items decode from the patched cache")
    out = run([os.path.join(JDK, "java.exe"), "-cp", base_cp(), "InspectItemModels",
               GAME_CACHE] + [str(i["newId"]) for i in items])
    names = [l for l in out.splitlines() if l.strip().startswith("opcode 2 =")]
    log(f"  decoded {len(names)} item names ({len(items)} expected)")
    if len(names) < len(items):
        raise SystemExit("verification failed: some items did not decode with a name")


def step_previews(items, defs):
    log("rendering inventory icon previews")
    os.makedirs(PREVIEWS, exist_ok=True)
    for item in items:
        key = item["rs3Id"]
        d = defs.get(key) or defs.get(str(key))
        cam = resolved_icon(item, d)
        name = (item.get("name") or d["name"]).replace(" ", "_").replace("'", "")
        out = os.path.join(PREVIEWS, f"{item['newId']}_{name}.png")
        run([os.path.join(JDK, "java.exe"), "-cp", base_cp(), "RenderClientItemIconSheet",
             GAME_CACHE, str(item["newId"]), str(cam["zoom"]), str(cam["xan"]), str(cam["yan"]),
             str(cam["zan"]), out, str(cam["xoff"]), str(cam["yoff"])])
    log(f"  previews in {PREVIEWS}")


def step_mirror():
    log(f"mirroring cache -> {CLIENT_CACHE}")
    os.makedirs(CLIENT_CACHE, exist_ok=True)
    for f in glob.glob(os.path.join(GAME_CACHE, "main_file_cache.*")):
        shutil.copy2(f, CLIENT_CACHE)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    p_run = sub.add_parser("run", help="run the full RS3 import pipeline")
    p_run.add_argument("--no-previews", action="store_true")
    p_run.add_argument("--no-mirror", action="store_true")
    p_run.add_argument("--only", help="comma-separated newIds to (re)import")
    args = parser.parse_args()

    only_ids = {int(x) for x in args.only.split(",")} if args.only else None
    items = load_manifest(only_ids)
    rs3_stats = load_rs3_stats()
    log(f"importing {len(items)} RS3 items")

    step_download_caches(items)
    step_compile()
    defs = step_extract_defs(items)
    step_extract_models(items, defs)
    step_plan(items, defs, rs3_stats)
    step_import()
    step_server_configs(items, defs, rs3_stats)
    step_verify(items)
    if not args.no_previews:
        step_previews(items, defs)
    if not args.no_mirror:
        step_mirror()
    log("DONE. Restart the server + client to see the items.")


if __name__ == "__main__":
    main()

