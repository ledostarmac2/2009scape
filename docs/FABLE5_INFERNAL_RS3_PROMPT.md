# Fable 5.0 handoff: Infernal cape + RS3 item polish

**Copy everything below this line into a new Fable 5.0 agent session.**

---

## Project context

You are working on **2009scape**, a RuneScape 2009-era private server revival with a custom item import pipeline that brings OSRS and RS3 items into the legacy 2009 client cache.

| | |
|---|---|
| **Repo path** | `C:\Users\btarabocchia\2009scape\2009scape` |
| **Player account** | `ledostar` (`game/data/players/ledostar.json`) |
| **Server port** | 43595 |
| **JRE** | `jre/bin/java.exe` in repo root |
| **Game dir** | `game/` (server.jar, client.jar, `data/cache/`) |

### Import pipelines

| Pipeline | Manifest | Runner | Docs |
|---|---|---|---|
| **OSRS** | `tools/osrs-import/manifest.json` | `powershell -ExecutionPolicy Bypass -File import-osrs-items.ps1` | `docs/osrs-item-import.md` |
| **RS3** | `tools/rs3-import/manifest.json` | `python tools/rs3_import_pipeline.py run` | `docs/rs3-item-import.md` |

Both pipelines compile Java tools in `tools/`, patch `game/data/cache/`, merge `game/data/configs/item_configs.json`, verify decode round-trip, render icon previews, and mirror cache files to `%USERPROFILE%\cache\runescape` (client reads from there).

**WIP bundle doc:** `docs/wip-infernal-rs3.md`

---

## What is DONE (do not break)

### OSRS polished batch: ids 14659–14705

Complete, gated, in ledostar bank. Includes ferocious gloves (14659), regen bracelet (14660), infernal max cape precursor items, imbued heart, avernic treads, scythe of vitur, twisted bow, etc. through soulreaper axe (14705).

- Manifest: `tools/osrs-import/manifest.json` (entries 14659–14705)
- These items passed icon QA, worn-model QA, and level gating
- **Do not remove these from ledostar bank**

---

## What is WIP (your task)

### Bundle A: RS3 items 14720–14733

Imported to cache and configs but **unpolished**. Stripped from ledostar bank/inventory.

| newId | Item | RS3 id | Cache |
|---|---|---|---|
| 14720 | Chaotic rapier | 18349 | 554 |
| 14721 | Chaotic longsword | 18351 | 554 |
| 14722 | Chaotic maul | 18353 | 554 |
| 14723 | Chaotic crossbow | 18357 | 554 |
| 14724 | Chaotic staff | 18359 | 554 |
| 14725 | Zaryte bow | 20171 | 839 |
| 14726 | Goliath gloves | 28444 | 286 |
| 14727 | Swift gloves | 28445 | 286 |
| 14728 | Spellcaster gloves | 28446 | 286 |
| 14729 | Royal crossbow | 24338 | 302 |
| 14730 | Drygore rapier | 26579 | 554 |
| 14731 | Drygore longsword | 26580 | 554 |
| 14732 | Drygore mace | 26582 | 554 |
| 14733 | Korasi's sword | 19784 | 554 |

**Known issues:**
- RS3 worn models use a different skeleton; importer nearest-bone rigs against native refs
- Several items use `wornTemplate` to borrow native worn geometry — may still look wrong
- RS3 icon cameras (`xan2d/yan2d`) often crash or mis-render; pipeline normalizes angles but per-item `iconZoom/iconXan/iconYan/iconZan/iconXoff/iconYoff` overrides in manifest may still need tuning after contact-sheet QA
- Ranged/magic weapons need server wiring to actually fire
- Stats from `data/import/rs3_items_stats.json` (not osrsreboxed)

**Key files:**
- `tools/rs3_import_pipeline.py`
- `tools/ExtractRs3ItemDefs.java`
- `tools/ExtractRs3Models.java`
- `tools/ImportOsrsItemBatch.java` (shared cache writer)
- `tools/rs3-import/manifest.json`

### Bundle B: Infernal cape 14734

| | |
|---|---|
| **newId** | 14734 |
| **OSRS id** | 21295 |
| **Status** | Imported but **client freezes** when equipped/viewed |

**Known issues — texture 59:**

- Infernal cape models reference **texture slot 59** (animated OSRS lava)
- **Fire cape (texture 40)** works: 23-byte `TextureOpSprite` pipeline + archive-8 sprite 485 (128×128 sheet), animation speed=0 dir=255
- **Infernal (texture 59)** should replicate that pattern with OSRS rev233 **sprite 318** (128×128 animated lava) installed at game sprite **768**
- Current patches freeze the client — likely `TextureOp` bytecode or sprite sheet encoding mismatch with 2009 client expectations
- `PatchInfernalCape.java` documents failed approaches: OSRS speed/dir (1/1), raw OSRS sprite bytes (format 0), sprite 768 stub without full pipeline clone

**Forum insight (brkownz, Zion):** The 2009 client texture system was not designed for OSRS rev233 procedural textures. Slot 59 must be **re-encoded** to native 2009 `TextureOpSprite` layout (like fire cape 40), not copied verbatim from OSRS.

**Key files:**
- `tools/PatchInfernalCape.java` — primary fix attempt (clone fire-cape TextureOp, patch sprite 768 from OSRS 318)
- `tools/PatchInfernalTexture.java` — alternate path (OSRS pixels + animation config into slot 59)
- `tools/osrs_import_pipeline.py` — calls infernal texture patch after batch import (search for "infernal lava texture 59")
- `tools/osrs-import/manifest.json` — entry 14734 with `rigRefMale/Female` 9638/9640 (fire cape), recolor map

---

## Success criteria

1. **Infernal cape 14734:** Animated lava on texture 59 renders when worn; **zero client freeze**; visually distinct from flat fire-cape recolor
2. **RS3 14720–14733:** Each item passes inventory icon QA (`tools/rs3-import/previews/`) and worn-model QA from multiple camera angles; optionally flip `"gated": true` and re-run pipeline
3. Re-add polished items to ledostar bank only after in-game QA (use `tools/_add_osrs_bank_items.py` pattern or manual bank edit — **never modify `core_data.location`**)
4. Update `docs/wip-infernal-rs3.md` and manifest `_status` fields from WIP to complete when done

---

## Commands

### OSRS pipeline (infernal cape)

```powershell
cd C:\Users\btarabocchia\2009scape\2009scape

# Full import + relaunch (stops game, patches cache, mirrors, starts server:43595 + client)
powershell -ExecutionPolicy Bypass -File import-osrs-items.ps1

# Infernal only, no relaunch
python tools/osrs_import_pipeline.py run --only 14734 --no-mirror
```

### RS3 pipeline

```powershell
cd C:\Users\btarabocchia\2009scape\2009scape

# Full RS3 batch
python tools/rs3_import_pipeline.py run

# Single item
python tools/rs3_import_pipeline.py run --only 14720
```

### Manual infernal texture patch (after cache edit)

```powershell
cd C:\Users\btarabocchia\2009scape\2009scape\tools
# Compile tools first (pipeline does this automatically)
java -cp ".;lib/*" PatchInfernalCape ..\game\data\cache ..\data\import\openrs2-osrs-2565-disk\cache
```

### Strip WIP items from ledostar (if re-adding for test then reverting)

```powershell
python tools/_strip_rs3_from_ledostar.py
```

---

## Relaunch procedure

The import scripts handle this automatically. Manual sequence:

1. **Stop** any running `jre/bin/java.exe` processes from this repo
2. **Run pipeline** (patches `game/data/cache/`)
3. **Mirror cache** to client dir (pipeline step 8/9):
   - Copies `main_file_cache.dat` + `main_file_cache.idx0`–`idx5` → `%USERPROFILE%\cache\runescape`
   - **Client will not see new models without this mirror**
4. **Start server first** on port 43595:
   ```powershell
   cd game
   ..\jre\bin\java.exe -Dsun.net.useExclusiveBind=false -Xmx2G -Xms2G -jar server.jar
   ```
5. **Start client** after server is listening:
   ```powershell
   ..\jre\bin\java.exe -Xmx1G -Xms1G -jar client.jar
   ```
6. Log in as ledostar, test with `::item 14734` or `::item 14720`

**Do not relaunch unnecessarily** when only editing player save JSON.

---

## Constraints

- **Never modify `core_data.location`** in ledostar.json
- **Do not touch OSRS polished items 14659–14705** in bank unless explicitly asked
- Player saves (`game/data/players/`) are gitignored — force-add if committing: `git add -f game/data/players/ledostar.json`
- Cache backups live in `game/osrs-import-backups/` (gitignored)
- `data/import/` caches are gitignored (downloaded from OpenRS2 on first run)

---

## Suggested investigation order

1. Read `tools/PatchInfernalCape.java` and compare texture 40 vs 59 bytecode side-by-side
2. Verify sprite 768 is rt4-encoded (not raw OSRS format 0) and matches fire-cape sprite 485 structure
3. Test infernal equip with client logging; confirm freeze happens at texture bind not model decode
4. Once infernal works, iterate RS3 items one at a time with `--only` and preview PNGs
5. Document fixes in `docs/wip-infernal-rs3.md`, clear WIP markers in manifests

---

**End of handoff prompt. Paste into Fable 5.0 and begin with infernal cape texture 59 investigation.**
