# Fable 5.0 handoff: Infernal cape (14734) + RS3 batch (14720–14733)

Use this prompt when handing the WIP bundle to a Fable 5.0 agent. The parent session **fixed the client startup hang** (texture loader stuck at "Loading textures — 0%") but infernal lava visuals and RS3 polish remain incomplete.

---

## Copy-paste prompt for Fable 5.0

```
You are working in C:\Users\btarabocchia\2009scape\2009scape (2009scape private server).

GOAL: Finish the WIP item bundle without breaking client startup.

## Context (read first)
- docs/wip-infernal-rs3.md — bundle status, blockers, success criteria
- docs/osrs-item-import.md — OSRS pipeline (14659–14705 shipped; 14734 WIP)
- docs/rs3-item-import.md — RS3 pipeline (14720–14733 WIP)
- tools/PatchInfernalCape.java — CURRENT infernal lava fix (procedural TextureOp, no sprite 768)
- tools/osrs-import/manifest.json — infernal 14734 entry (_status: WIP)
- tools/rs3-import/manifest.json — RS3 14720–14733 entries (_status: WIP)

## What is DONE (do not regress)
- OSRS items 14659–14705: polished, gated, player-ready
- Client loads past "Loading textures" to login (texture 59 no longer hangs loader)
- Procedural TextureOp in archive 9 group 59 + fire-cape animation driver on archive 26 slot 59
- WIP items stripped from ledostar bank/inventory/equipment (tools/_strip_rs3_from_ledostar.py)
- ledostar core_data.location must NEVER be modified by bank/import scripts

## PRIORITY 1 — Infernal cape (14734) worn lava
Problem: Infernal cape models reference texture slot 59 for animated lava. Previous commits installed
a fire-cape-clone TextureOpSprite pipeline + archive-8 sprite 768 (OSRS sprite 318 import). That broke
the startup texture loader (hang at 0%) and still froze the client when worn.

Current safe baseline (PatchInfernalCape.java):
- 44-byte procedural TextureOp in archive 9 group 59 (NO archive-8 sprite reference)
- Archive 26 config slot 59: animated=true, speed/dir copied from fire cape 40, avgColor=3875
- TestTextureProvider confirms id 59 loads (16384 pixels, available=true)

Your task:
1. Make infernal cape lava look like OSRS infernal (animated orange lava), NOT flat fire-cape recolor
2. Must NOT hang client at startup OR when equipping/viewing 14734
3. Working reference: fire cape texture 40 — 23-byte TextureOpSprite + archive-8 sprite 485 (128×128 rt4 sheet)
4. If re-introducing sprite sheets, use rt4-encoded format (see fire cape 485), NOT raw OSRS format 0 bytes
5. Sprite 768 is reserved for infernal lava; do NOT clobber native sprite 318
6. Run: TestTextureProvider game/data/cache 40 59 after every cache change
7. Mirror cache: copy game/data/cache/main_file_cache.* → %USERPROFILE%\cache\runescape
8. Re-test: launch client, pass "Loading textures", equip ::item 14734, verify no freeze

Key tools:
  javac/java from C:\Users\btarabocchia\Java\temurin-26.0.1+8\bin
  java -cp "tools;game/client.jar" PatchInfernalCape game/data/cache
  java -cp "tools;game/client.jar" TestTextureProvider game/data/cache 40 59
  python tools/osrs_import_pipeline.py run --only 14734

## PRIORITY 2 — RS3 items 14720–14733
Imported via python tools/rs3_import_pipeline.py run. In cache + item_configs.json but unpolished.

Per-item QA needed:
- Inventory icon (tools/rs3-import/previews/)
- Worn model from multiple camera angles (nearest-bone rig vs wornTemplate)
- Combat wiring for weapons (configTemplate, attackSpeed, server handlers)
- Optional gating: "gated": true in manifest after polish

Items:
  14720–14724 Chaotic weapons (cache 554)
  14725 Zaryte bow (839)
  14726–14728 Dominion gloves (286)
  14729 Royal crossbow (302)
  14730–14732 Drygore weapons (554)
  14733 Korasi sword (554)

## PRIORITY 3 — Player save policy
Only re-add WIP items to ledostar AFTER both bundles pass in-game QA.
Strip script: python tools/_strip_rs3_from_ledostar.py (never touches location)

## PRIORITY 4 — Launch verification
1. taskkill /F /IM java.exe
2. Mirror cache to %USERPROFILE%\cache\runescape
3. launch.bat from repo root (server 43595, then client)
4. Confirm: past "Loading textures", login OK, no error_game_js5crc

## Constraints
- Do NOT remove infernal 14734 or RS3 14720–14733 from manifests/configs — keep as WIP
- Do NOT modify ledostar location
- Do NOT break shipped OSRS 14659–14705
- Commit with clear messages; push to origin/master when user requests
```

---

## Root cause: startup texture hang (fixed in parent session)

| Symptom | Cause |
|---|---|
| Client stuck at **"Loading textures — 0%"** | `Js5GlTextureProvider` preloads every enabled texture at startup |
| Hang / freeze | Archive 9 group 59 had a **TextureOpSprite pipeline** referencing archive-8 **sprite 768** with malformed data (raw OSRS import or incomplete stub, 156b packed vs fire-cape 485 at 6987b) |
| Config mismatch | Archive 26 slot 59 was `animated=false speed=0 dir=0 avgColor=108` instead of fire-cape driver |
| `error_game_js5crc` in logs | Server cache patched but client `%USERPROFILE%\cache\runescape` stale — mirror after every cache edit |

**Fix applied:** `PatchInfernalCape.java` installs a **44-byte procedural TextureOp** (no sprite dependency) and copies fire-cape animation driver (speed=0, dir=255) to config slot 59.

**Verification (parent session):**
```
TestTextureProvider game/data/cache 40 59
  id 40: available=true pixels=16384
  id 59: available=true pixels=16384 avgColor=3875
```

---

## File map

| Path | Purpose |
|---|---|
| `tools/PatchInfernalCape.java` | Procedural infernal lava patch (startup-safe) |
| `tools/PatchInfernalTexture.java` | Deprecated wrapper → PatchInfernalCape |
| `tools/ParseTextureConfigBundle.java` | Decode/encode archive 26 texture config bundle |
| `tools/TestTextureProvider.java` | Verify texture ids load without hang |
| `tools/CheckSpriteGroups.java` | Inspect archive-8 sprite groups (485, 768, 318) |
| `tools/osrs_import_pipeline.py` | Full OSRS import; calls PatchInfernalCape for 14734 |
| `tools/rs3_import_pipeline.py` | Full RS3 import for 14720–14733 |
| `tools/_strip_rs3_from_ledostar.py` | Remove WIP ids from player save |
| `game/data/cache/main_file_cache.*` | Patched server cache (texture 59 fixed) |
| `game/data/configs/item_configs.json` | Server item definitions |
| `docs/wip-infernal-rs3.md` | Bundle status doc |

---

## Success criteria

1. **Startup:** Client reaches login; no hang at "Loading textures"
2. **Infernal worn:** Texture 59 animated lava renders correctly on 14734; no freeze
3. **RS3 batch:** Each 14720–14733 passes icon + worn QA; weapons fire in combat
4. **Player:** Re-add to ledostar bank only after QA; location unchanged
5. **Cache sync:** Always mirror to `%USERPROFILE%\cache\runescape` after cache edits

---

## JDK note

Compile/run cache tools with **Temurin 26** (`C:\Users\btarabocchia\Java\temurin-26.0.1+8\bin`).
Bundled `jre\` is Java 11 and cannot load classes compiled for Java 26.
