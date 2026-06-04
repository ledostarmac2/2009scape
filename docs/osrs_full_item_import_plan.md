# OSRS Full Item Import Plan

Generated for this private 2009Scape fork on 2026-06-04.

## Scope

The goal is a repeatable import pipeline for Old School RuneScape items added after the 2013 relaunch. The pipeline must preserve the original 2009Scape item set, add imports as custom expansion content, and prove one prototype item before any batch cache write.

This document intentionally does not approve a bulk import. It records Phase 1 findings, source options, assumptions, and the safe next steps.

## Hard Rules

- Never overwrite an existing 2009Scape item ID.
- Never overwrite a model ID without a backup and rollback path.
- Treat the original cache and config files as authoritative historical content.
- Imported OSRS items get custom 2009Scape IDs when an ID conflict exists.
- Cache writes stay disabled until one prototype item works in inventory, on ground, and equipped.
- Do not push to GitHub without explicit approval.

## Phase 1 Findings

### Item Definitions

Primary server-side item definitions are in:

- `game/data/configs/item_configs.json`

Local inspection found:

- Item count: `11981`
- Highest configured item ID: `14658`
- Existing custom item example: `14422 Emberblade`
- Item fields include:
  - `id`
  - `name`
  - `examine`
  - `tradeable`
  - `bankable`
  - `destroy`
  - `equipment_slot`
  - `bonuses`
  - `requirements`
  - `weapon_interface`
  - `attack_speed`
  - animation/audio fields such as `attack_anims`, `stand_anim`, `walk_anim`, `run_anim`, `equip_audio`

The server class `core.cache.def.impl.ItemDefinition` also loads client/cache-backed fields such as:

- `interfaceModelId`
- `modelZoom`
- `modelRotationX`
- `modelRotationY`
- `modelOffsetX`
- `modelOffsetY`
- `stackable`
- `value`
- `maleWornModelId1`
- `femaleWornModelId1`
- additional worn model IDs
- ground and inventory actions
- recolors and retextures
- note/lend/recolor template IDs
- client script data

### Examine Text

`examine` is embedded per item in `game/data/configs/item_configs.json`.

The cache item definition may also include item names and model/action metadata, but the server-side examine text used by gameplay is in the JSON config.

### Equipment Data

Equipment slot and combat metadata are in `game/data/configs/item_configs.json`:

- `equipment_slot`
- `two_handed`
- `remove_head`
- `remove_beard`
- `remove_sleeves`
- `hat`
- `requirements`

The equipment slot values are numeric strings. Examples from this cache:

- `0`: head
- `1`: cape
- `2`: amulet
- `3`: weapon
- `4`: body
- `5`: shield
- `7`: legs
- `9`: gloves
- `10`: boots
- `12`: ring

### Bonuses

Item combat bonuses are stored as a comma-separated 15-value string in `item_configs.json`.

Observed order from existing items and `WeaponInterface` constants:

1. stab attack
2. slash attack
3. crush attack
4. magic attack
5. ranged attack
6. stab defence
7. slash defence
8. crush defence
9. magic defence
10. ranged defence
11. summoning/absorb style slot depending on local use
12. strength
13. prayer
14. reserved/unused local slot
15. reserved/unused local slot

Assumption: the first 13 values are safe to map directly for normal melee/ranged/magic/prayer equipment; values 14-15 need local verification before automated mapping.

### Weapon Styles and Attack Speeds

Weapon metadata is in `item_configs.json`:

- `weapon_interface`
- `attack_speed`
- `attack_anims`
- `attack_audios`
- `has_special`
- `fun_weapon`

Ranged weapon specifics are split into:

- `game/data/configs/ranged_weapon_configs.json`
- `game/data/configs/ammo_configs.json`

### Client and Cache Item Configs

The render-facing item definition data is in the JS5 cache:

- `game/data/cache/main_file_cache.dat2`
- `game/data/cache/main_file_cache.idx*`

Relevant archives identified from the existing Emberblade patch tool:

- archive `19`: item definitions
- archive `7`: models
- archive `255`: master index

The Java cache classes in `server.jar` include:

- `core.cache.Cache`
- `core.cache.CacheFile`
- `core.cache.CacheFileManager`
- `core.cache.def.impl.ItemDefinition`

The client-side cache classes used by prior tooling include `rt4.Cache`, `rt4.Js5Index`, and `rt4.Js5Compression`.

### Custom Item IDs

Custom item IDs are already supported in practice.

Evidence:

- `game/data/configs/item_configs.json` contains custom/high entries such as `14422 Emberblade`.
- The player save and admin/item spawn path can reference those IDs once server and cache definitions exist.

Policy:

- New OSRS imports should start above the current max configured ID, currently `14658`, unless a permanent mapping file says otherwise.

### Custom Model IDs

Custom model IDs are possible but not yet safe for batch use.

Evidence:

- `tools/PatchEmberbladeVisual.java` writes archive `7` model groups and archive `19` item definition groups.
- It backs up `main_file_cache.dat2`, `idx7`, `idx19`, and `idx255`.

Blocker:

- The Emberblade model path was bespoke and fragile. It used OBJ-to-old-model encoding, hand tuning, and skeleton/attachment work. This is not yet a generic OSRS model conversion pipeline.

### Cache Editing or Packing Tools

Existing local tools:

- `tools/PatchEmberbladeVisual.java`
- `tools/InspectItemModels.java`
- `tools/VerifyEmberbladeVisual.java`
- `tools/DumpJs5.java`
- cache dumps under `tools/cache-dump/`

These are useful prototypes, not production batch importers.

### Item Spawning and Admin Commands

The server jar contains `core.game.system.command.sets.SpawnCommandSet`. The local admin command system can spawn items by ID. Imported items should become spawnable once:

1. `item_configs.json` has the server definition.
2. cache archive `19` has a matching client item definition.
3. model references in archive `19` point at valid archive `7` model IDs.

## Phase 2 Source of Truth

### Recommended Source Chain

Primary source:

- OpenRS2 Archive for current OSRS cache acquisition.

OpenRS2 documents that it archives caches and XTEA keys for all OSRS builds and exposes JSON/cache download API endpoints. The API provides `/caches.json`, disk cache downloads, flat-file downloads, keys, and individual archive/group fetches.

Decoder:

- RuneLite `cache` module for decoding OSRS item definitions and model references.

RuneLite is preferable for item definition decoding because it already tracks OSRS cache format changes and has dedicated cache tooling.

Secondary enrichment sources:

- RuneLite item stats/data where available.
- OSRS Wiki-derived exports for examine, requirements, weapon speed, and GE/trade metadata.
- Manual review for any item where sources disagree.

Rejected or caution sources:

- OSRSBox blog pages appeared in search results, but the pages currently redirect to unrelated spam content. Do not trust them unless a clean archived copy or source repo is verified.
- Random RSPS cache tools may be useful for inspection, but should not be trusted for writes until compatibility with this 2009Scape cache revision is proven.
- OBJ exports are useful for Blender inspection but are not sufficient as a final cache format.

### Desired OSRS Source Schema

Place the normalized item dump at:

- `data/import/osrs_items.json`

The comparison scaffold accepts a JSON list, a dict keyed by ID, or a dict with an `items` list.

Target fields:

- `id`
- `name`
- `examine`
- `stackable`
- `tradeable`
- `inventory_model` or `inventoryModel`
- `ground_model` if available
- `male_wield_model`
- `female_wield_model`
- `zoom`
- `rotation_x`
- `rotation_y`
- `offset_x`
- `offset_y`
- `equipment_slot`
- `weapon_type`
- `attack_speed`
- `bonuses`
- `requirements`
- `inventory_options`
- `ground_options`
- note/placeholder relationships

## Phase 3 Comparison Tool

Created:

- `tools/osrs_item_importer.py`

Outputs:

- `data/import/osrs_item_diff.json`
- `data/import/osrs_item_diff.csv`

Current dry run status:

- Blocked because `data/import/osrs_items.json` does not exist yet.
- Local 2009Scape item count was read successfully.
- No item configs or cache files were modified.

The diff classifies:

- same ID existing items
- same name/different ID items
- OSRS-only items
- duplicate names
- null/placeholder items
- noted/note-related items
- tradeable/untradeable hints
- equipment items
- weapon items
- definition-only candidates
- model-required candidates

## Phase 4 ID Mapping Strategy

Created:

- `data/import/osrs_to_2009scape_item_id_map.json`
- `data/import/osrs_to_2009scape_model_id_map.json`

Rules:

- Existing 2009Scape IDs are never overwritten.
- If the OSRS ID is unused locally, it still requires review before reuse.
- If the OSRS ID conflicts locally, assign a custom ID above the current max.
- Current safe custom starting point is `14659`.
- Model IDs are separate and must not be assumed to match item IDs.

Import statuses:

- `pending`
- `definition_imported`
- `model_imported`
- `equipment_configured`
- `tested_inventory`
- `tested_ground`
- `tested_equipped`
- `blocked`
- `skipped`

## Phase 5 Prototype Plan

Do not batch import yet.

Pick one modern OSRS weapon that:

- does not exist in 2009Scape by ID or name
- has simple, non-textured geometry if possible
- has a clear one-handed or two-handed equipment slot
- has known bonuses and attack speed
- has available inventory and wield model IDs from the OSRS cache dump

Prototype steps:

1. Add mapping entry with `pending`.
2. Assign a safe custom 2009Scape item ID.
3. Assign safe model IDs above the local model range after scanning archive `7`.
4. Backup cache files.
5. Generate one server item config entry.
6. Generate one archive `19` item definition.
7. Convert or copy one inventory model and one worn model into archive `7`.
8. Spawn item by ID.
9. Test inventory icon.
10. Drop and inspect ground model.
11. Equip on male character.
12. Equip on female character.
13. Verify animations keep the model attached.
14. Mark statuses through `tested_equipped` only after success.

## Phase 6 Batch Import Architecture

The future importer should support:

- `dry-run`
- `import-one-id`
- `import-one-name`
- `import-all-pending-definition-only`
- `import-all-pending-equipment`
- `validate`
- `rollback-last-import-batch`

The scaffold already defines these commands. Import commands intentionally return blocked until Phase 5 proves the prototype.

Batch importer rules:

- Always backup before writing.
- Always support dry run.
- Every batch receives a batch ID and manifest.
- Every changed file is listed in the batch manifest.
- Rollback restores backups and mapping statuses.
- Cache writes happen only after validation passes.

## Phase 7 Model Conversion and Cache Packing

Open questions:

- Can current OSRS model binary data be reused directly in this 2009Scape client/cache revision?
- Are current OSRS model opcodes compatible with this client?
- Are textures, alpha, and newer material behavior compatible?
- Do inventory model rotations and offsets need conversion?
- Do male/female wield models use compatible skeleton/attachment metadata?
- Can RuneLite-exported models be converted back to this cache's older model format without losing attachment data?

Known local facts:

- Archive `7` model writes are possible.
- Archive `19` item definition writes are possible.
- OBJ inspection is not enough for production import.
- Prior OBJ-based model packing required custom old-model encoding and hand tuning.

Current status:

- Full model conversion is blocked pending one modern OSRS prototype.
- Definition and mapping scaffolding can continue safely.

## Phase 8 Validation

Validation requirements:

- No existing 2009Scape item ID overwritten.
- No existing model ID overwritten.
- Every imported item has a non-empty name.
- Every imported item has examine text or a placeholder.
- Every equipment item has `equipment_slot`.
- Every weapon has `weapon_interface` and `attack_speed`.
- Every item with model references points to valid archive `7` groups.
- Every mapped OSRS item has a valid import status.
- Duplicate names are reported.
- Noted/unnoted pairs are mapped consistently.
- Placeholder/null items are skipped unless explicitly approved.

Current validation:

- `python tools/osrs_item_importer.py validate`
- Passes for the empty scaffold.

## Phase 9 Usage Documentation

Initial workflow:

1. Obtain a verified OSRS cache through OpenRS2.
2. Decode item definitions using RuneLite cache tooling into `data/import/osrs_items.json`.
3. Run:
   - `python tools/osrs_item_importer.py dry-run`
4. Review:
   - `data/import/osrs_item_diff.json`
   - `data/import/osrs_item_diff.csv`
   - `data/import/osrs_to_2009scape_item_id_map.json`
5. Pick one prototype item.
6. Implement and test only that item.
7. Expand importer writes only after prototype success.

## Phase 10 Current Report

### Inspected

- Server item config JSON.
- Ranged/ammo config JSON.
- Cache data directory.
- ItemDefinition server class.
- Weapon and weapon interface classes.
- Spawn command class presence.
- Existing custom Emberblade cache patch tooling.

### Source Selection

- Primary cache source: OpenRS2 Archive.
- Primary decoder: RuneLite cache module.
- Secondary metadata: OSRS Wiki/RuneLite-derived data, manually cross-checked.

### Full Automation Feasibility

Definition comparison and ID mapping are feasible.

Server-side config generation is likely feasible after field mapping is finalized.

Full model automation is not yet proven. It is blocked on model-format compatibility, texture/recolor handling, and equip attachment validation.

### Prototype Status

No OSRS prototype item was imported in this phase.

### Files Created

- `docs/osrs_full_item_import_plan.md`
- `tools/osrs_item_importer.py`
- `data/import/osrs_item_diff.json`
- `data/import/osrs_item_diff.csv`
- `data/import/osrs_to_2009scape_item_id_map.json`
- `data/import/osrs_to_2009scape_model_id_map.json`
- `data/import/.gitkeep`

### Safe Next Step

Create or fetch a verified OSRS item dump at `data/import/osrs_items.json`, then rerun:

```powershell
python tools/osrs_item_importer.py dry-run
python tools/osrs_item_importer.py validate
```

After reviewing the diff, choose one simple modern OSRS item for Phase 5 prototype import.

## External References

- OpenRS2 Archive: https://archive.openrs2.org/
- OpenRS2 API: https://archive.openrs2.org/api
- RuneLite repository: https://github.com/runelite/runelite
