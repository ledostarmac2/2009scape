# OSRS → 2009scape item import pipeline

Bring any OSRS item into 2009scape with **one manifest entry and one command**.
Everything learned the hard way (icon cameras, model orientation, rigging,
z-fighting, cache index surgery) is baked into the tooling — you should never
need to touch model bytes or item opcodes by hand again.

```
1. Edit  tools\osrs-import\manifest.json     (add {"newId": ..., "osrsId": ...})
2. Run   powershell -ExecutionPolicy Bypass -File import-osrs-items.ps1
3. Log in, spawn with  ::item <newId>,  try it on.
4. Happy? Flip "gated": true in the manifest, re-run -> OSRS level reqs applied.
```

---

## The moving parts

| Path | Role |
|---|---|
| `tools\osrs-import\manifest.json` | **THE ONLY FILE YOU EDIT.** One entry per item. |
| `import-osrs-items.ps1` (repo root) | One-command runner: stop game → pipeline → relaunch. |
| `tools\osrs_import_pipeline.py` | Orchestrator (compile, extract, plan, import, configs, verify, previews, mirror). |
| `tools\ExtractOsrsItemDefs.java` | Dumps OSRS item defs (models, icon camera, flags) from the OSRS cache. |
| `tools\ExtractOsrsModels.java` | Dumps OSRS model containers → `data\import\osrs-model-groups\<id>.container`. |
| `tools\ImportOsrsItemBatch.java` | Writes models (archive 7) + item defs (archive 19) into the 2009 cache. Creates brand-new group/file ids via index reserialization. |
| `tools\osrs-import\plan.txt` / `extracted.json` | Generated intermediates (never edit). |
| `tools\osrs-import\previews\` | Rendered inventory icons (exact client sprite path) for eyeball QA. |
| `data\import\openrs2-osrs-2565-disk\cache` | The local OSRS cache (source of truth for models/cameras). |
| `data\import\osrs_items.json` | osrsreboxed stat dump (bonuses, requirements, slots, examine). |
| `game\data\configs\item_configs.json` | Server-side stats — the pipeline merges entries automatically. |
| `game\osrs-import-backups\` | First-run backups of the four cache files. |

## Manifest fields

```jsonc
{
  "newId": 14702,          // REQUIRED - next free 2009scape id (see _nextFreeId)
  "osrsId": 11832,         // REQUIRED - from the OSRS wiki infobox
  "name": null,            // optional, defaults to OSRS name
  "examine": null,         // optional, defaults to OSRS examine
  "configTemplate": 1305,  // weapons: native item whose anims/interface are cloned
  "attackSpeed": 5,        // weapons: ticks (overrides template)
  "wornPriority": null,    // render priority override (defaults by slot)
  "rigRefMale": null,      // rigging override (defaults by slot)
  "rigRefFemale": null,
  "wornDy": 0,             // vertical nudge for worn models (+ = down)
  "skipWorn": false,       // inventory-only import (rings, ingredients)
  "requirements": null,    // explicit "{0,75}-{2,75}" override
  "gated": false           // false = try-on mode, true = apply level reqs
}
```

### Weapon config templates (clone anims/interface from a native item)

| Weapon type | template id | item |
|---|---|---|
| Stab spear / lance / hasta | 11379 | Bronze hasta |
| Sword / scimitar | 1305 | Dragon longsword |
| Warhammer / mace | 1347 | Rune warhammer |
| Crossbow | 9185 | Rune crossbow |
| Bow / thrower | 861 | Magic shortbow |
| Defender (shield slot) | 8850 | Rune defender |

### Per-slot defaults (only override when something looks wrong)

| Slot | rig ref (M/F) | worn priority |
|---|---|---|
| head | 21873 / 21906 | 7 (chathead 4) |
| cape | 9638 / 9640 (fire cape) | 0 |
| neck | 9642 / 9644 (fury) | 4 |
| weapon / 2h | 5409 (whip) | 10 |
| shield | 15413 (rune defender) | 10 |
| hands | 13307 / 13319 (barrows gloves) | 10 |
| feet | 27738 / 27754 (dragon boots) | 0 |
| ring / ammo | (no worn model) | — |

## What the pipeline guarantees (and why)

- **Icons are pixel-perfect by construction.** Inventory/ground models are imported
  **identity** (no scaling, no axis swap) and the authentic OSRS
  `zoom2d/xan2d/yan2d/zan2d/xOffset2d/yOffset2d` camera is copied into the item def.
  The 2009 icon renderer shares the RS2 sprite math with OSRS, so this lines up
  exactly. (Lesson learned: any transform on the inventory model makes the icon
  untunable.)
- **Worn models rig themselves when possible.** If the OSRS model still carries
  vskin (vertex group) labels they are used verbatim — OSRS kept the RS2 skeleton
  numbering. If not, vertices inherit the nearest bone from a native 2009 reference
  model of the same slot (never the root bone 0, which causes jagged static spikes).
- **New cache ids are created safely.** Model ids above the 2009 ceiling are added
  as new archive-7 groups; new item ids are added as new files inside their
  archive-19 group, with the JS5 index reserialized (round-trip-validated codec).
- **Server stats come from real OSRS data.** Bonuses map osrsreboxed →
  2009 15-slot array (`atk x5, def x5, summoning=0, str, prayer, 0, 0`); slots,
  two-handed, tradeable and examine likewise. Weapons additionally clone the
  anims/interface of a native template item so they animate correctly.
- **Try-on first, gate later.** `"gated": false` (default) imports without level
  requirements. Flip to `true` and re-run; the OSRS requirements (or your explicit
  `requirements` string) are written into `item_configs.json`.
  Skill ids: 0 atk, 1 def, 2 str, 3 hp, 4 range, 5 pray, 6 magic.

## Verifying a new import

The pipeline already: decodes every imported item back out of the patched cache
(fails loudly if anything is corrupt) and renders the inventory icon through the
real client sprite path into `tools\osrs-import\previews\`. Eyeball those PNGs —
if an icon looks right there, it looks right in game.

In game: `::item <newId>` then equip it. Check worn appearance from a few angles.

## When something looks off in-game

| Symptom | Fix |
|---|---|
| Ring/amulet says "You can't wear that" | OSRS rings have no worn model; the pipeline now writes the client **Wear** option via `equippable` even when `skipWorn` is true. Re-run the import. |
| Cape draws under body armour | Capes inherit **per-face priorities from the fire-cape rig ref** (9638/9640). Override with `"facePriority": N` only if you need a uniform override. |
| Weapon/offhand floats too high | Weapon/shield/2h slots auto-apply **`wornDy: 11`** — the net ferocious-gloves rig-center delta. Override per item in the manifest. |
| Worn model floats / sinks | Set `wornDy` (+down/−up, model units; ±5–15 typical) and re-run with `--only <id>`. |
| Worn model z-fights the body | Bump `wornPriority` by slot (gloves/weapons want 10); if it draws *over* everything, lower it. |
| Worn model deforms badly when animating | OSRS model had no vskins and the nearest-bone guess failed: pick a closer-shaped native item's worn model as `rigRefMale`/`rigRefFemale` (find ids with `InspectItemModels <cache> <nativeItemId>` → opcodes 23/25). |
| Weapon swings with wrong anims | Use a different `configTemplate` (see table above). |
| Icon wrong | It won't be — but if OSRS itself changed the camera, re-run; cameras re-extract every time. |

Deep-dive fixes (cuff flares, per-item geometry surgery) live in
`tools\ImportOsrsItemModels.java` — that tool owns the three original imports
(14659–14661) and is the place for bespoke geometry hacks if an item ever needs one.

## Known limits

- **Ranged strength / magic damage** aren't part of the 2009 bonus array; ranged
  weapons also need `ranged_weapon_configs.json` / ammo wiring to actually fire.
  Imported ranged weapons are try-on/melee-stat complete only.
- **Special attacks** need a server-side handler (`tools\custom-src\*SpecialHandler.java`);
  the pipeline never sets `has_special`.
- **Textured OSRS faces** are flattened to a representative colour (the 2009 model
  format used here doesn't carry OSRS texture mappings).
- **Quest/diary requirements** (e.g. Fremennik Exile for the faceguard) can't be
  expressed in `requirements` — only skill levels.
- **Korasi's sword** (RS3 id 19784) is not in the OSRS cache; use the RS3 import
  pipeline instead of this manifest.
- **Player saves** (`game/data/players/`) are runtime state. Import/bank scripts
  must **never** modify `core_data.location` — leave the player where they last
  logged in.
