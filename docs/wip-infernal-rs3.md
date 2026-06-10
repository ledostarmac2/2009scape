# WIP bundle: Infernal cape (14734) + RS3 items (14720–14733)

**Status:** Unpolished — in game cache and server configs, but **removed from player bank/inventory** until fixed.

## What is complete (do not regress)

| Id range | Pipeline | Status |
|---|---|---|
| **14659–14705** | OSRS (`tools/osrs-import/manifest.json`) | **Polished and shipped** — ferocious gloves through soulreaper axe, gated, bank-ready |
| **14720–14733** | RS3 (`tools/rs3-import/manifest.json`) | **WIP** — imported to cache/configs; worn models and icon cameras need QA |
| **14734** | OSRS infernal cape (`tools/osrs-import/manifest.json`) | **WIP** — startup hang **fixed** (procedural texture 59); worn lava visuals still need OSRS-quality polish |

Next free id after this bundle: **14735** (both manifests agree).

---

## Infernal cape (14734) — known blocker

- **OSRS id:** 21295 → **2009scape id:** 14734
- **Startup hang (fixed):** Previous `TextureOpSprite` + sprite **768** pipeline hung the client at **"Loading textures — 0%"** because `Js5GlTextureProvider` preloads all enabled textures and the malformed sprite 768 / wrong config slot 59 (`animated=false`) stalled the loader.
- **Current safe baseline:** `PatchInfernalCape.java` installs a **44-byte procedural TextureOp** in archive 9 group 59 (no archive-8 dependency) and copies fire-cape animation driver to archive 26 slot 59. `TestTextureProvider` confirms ids 40 and 59 load (16384 pixels each).
- **Remaining work:** Worn infernal lava should match OSRS infernal (animated orange sheet), not procedural placeholder. Re-introduce sprite sheets only with **rt4-encoded** format like fire-cape sprite 485.
- **Working reference:** Fire cape uses **texture 40** with a 23-byte `TextureOpSprite` pipeline + archive-8 sprite 485 (128×128 sheet). Animation driver: speed=0, dir=255.
- **Infernal target:** OSRS rev233 **sprite 318** (128×128 animated lava sheet), installed at game sprite **768** (avoids clobbering native sprite 318). Texture **59** should mirror the fire-cape `TextureOp` pattern but swap the sprite group id.
- **Failed approaches documented in code:**
  - Keeping OSRS speed/dir (1/1) with a fire-cape `TextureOp` pipeline
  - Importing raw OSRS sprite bytes (format 0) instead of rt4-encoded sheets
  - Using sprite 768 as a stub without the full fire-cape pipeline clone
- **Key tools:**
  - `tools/PatchInfernalCape.java` — current fix: procedural TextureOp + animated config (startup-safe)
  - `tools/PatchInfernalTexture.java` — deprecated wrapper → PatchInfernalCape
  - Invoked from `tools/osrs_import_pipeline.py` after batch import

### Forum / community insight (texture 59 vs client limits)

Community discussion (brkownz, Zion on 2009scape forums) notes that the 2009 client's texture system was not designed for OSRS rev233 procedural/animated textures. Slot 59 exceeds what the legacy renderer safely handles when the `TextureOp` chain or sprite sheet encoding does not match native 2009 patterns. The fire cape (40) works because it uses the original 2009 `TextureOpSprite` bytecode layout; infernal (59) must be **re-encoded** to that layout, not copied verbatim from OSRS.

---

## RS3 items (14720–14733) — known limits

Imported via `tools/rs3_import_pipeline.py`. Items exist in cache and `item_configs.json` but are **not player-ready**.

| newId | RS3 item | Notes |
|---|---|---|
| 14720–14724 | Chaotic weapons | OpenRS2 cache 554; some need `wornTemplate` |
| 14725 | Zaryte bow | Cache 839; icon camera overrides applied |
| 14726–14728 | Dominion gloves | Cache 286; icon angles normalized |
| 14729 | Royal crossbow | Cache 302 |
| 14730–14732 | Drygore weapons | Cache 554; heavy icon camera tuning |
| 14733 | Korasi's sword | Cache 554; not in OSRS cache |

**Open issues:**

- **Worn models:** RS3 skeleton differs from 2009/OSRS; importer nearest-bone rigs against native refs. Several items use `wornTemplate` to borrow native worn geometry.
- **Icon cameras:** RS3 raw `xan2d/yan2d` values crash or mis-render; pipeline normalizes via `normalizeIconAngle` but per-item manifest overrides still needed after contact-sheet QA (`tools/rs3-import/previews/`).
- **Combat:** Ranged/magic weapons need server wiring to actually fire; stats from `data/import/rs3_items_stats.json`.

Full pipeline docs: `docs/rs3-item-import.md`

---

## Player save policy

WIP items are stripped from `game/data/players/ledostar.json` (bank + inventory + equipment) by:

```
python tools/_strip_rs3_from_ledostar.py
```

**Never modifies `core_data.location`.** OSRS polished items **14659–14705** are untouched.

---

## Success criteria (future work)

1. **Infernal cape:** Animated lava on texture 59 renders correctly when worn; **no client freeze**; visually matches OSRS infernal (not flat fire-cape recolor).
2. **RS3 batch:** Each of 14720–14733 passes inventory icon QA, worn-model QA from multiple angles, and optional gating (`"gated": true`).
3. Re-add to ledostar bank only after both bundles pass in-game QA.

---

## Related docs

- `docs/osrs-item-import.md` — OSRS pipeline (14659–14705 complete; 14734 WIP)
- `docs/rs3-item-import.md` — RS3 pipeline (14720–14733 WIP)
- `docs/FABLE5_INFERNAL_RS3_PROMPT.md` — handoff prompt for Fable 5.0 agent
