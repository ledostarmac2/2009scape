# RS3 → 2009scape item import pipeline

> **WIP bundle (14720–14733):** Items are imported to cache and configs but marked
> unpolished. Stripped from player bank until worn-model and icon QA pass.
> Bundled with infernal cape 14734 — see **`docs/wip-infernal-rs3.md`**.

Imports RS3-only items (not present in the OSRS cache) into 2009scape using the same
cache writer as the OSRS pipeline.

```
1. Edit  tools\rs3-import\manifest.json     (add {"newId": ..., "rs3Id": ..., "rs3CacheId": ...})
2. Run   python tools\rs3_import_pipeline.py run
3. Log in, spawn with  ::item <newId>,  try it on.
```

## Key paths

| Path | Role |
|---|---|
| `tools\rs3-import\manifest.json` | **THE ONLY FILE YOU EDIT** for RS3 items. |
| `tools\rs3_import_pipeline.py` | Orchestrator (download, extract, import, configs, verify). |
| `tools\ExtractRs3ItemDefs.java` | Dumps RS3 item defs from per-era OpenRS2 caches. |
| `tools\ExtractRs3Models.java` | Dumps RS3 model containers → `data\import\rs3-model-groups\`. |
| `tools\ImportOsrsItemBatch.java` | Shared cache writer (RS3 opcode pass-through for 44–54, 249). |
| `data\import\rs3_items_stats.json` | Hand-authored server stats (no osrsreboxed for RS3). |
| `data\import\openrs2-rs3-{id}-disk\cache` | Downloaded RS3 caches (auto-fetched from OpenRS2). |

## Manifest fields

Same as OSRS manifest, but use `rs3Id` + `rs3CacheId` instead of `osrsId`.
OpenRS2 download: `https://archive.openrs2.org/caches/runescape/{rs3CacheId}/disk.zip`

## Imported items (14720–14733)

| newId | RS3 item | OpenRS2 cache |
|---|---|---|
| 14720–14724 | Chaotic weapons | 554 |
| 14725 | Zaryte bow | 839 |
| 14726–14728 | Dominion gloves | 286 |
| 14729 | Royal crossbow | 302 |
| 14730–14732 | Drygore weapons | 554 |
| 14733 | Korasi's sword | 554 |

## Icon cameras

RS3 item defs carry authentic `zoom2d/xan2d/yan2d/zan2d/xOffset2d/yOffset2d` (opcodes 4–8;
`ExtractRs3ItemDefs.java`). The pipeline:

1. Extracts cameras from the RS3 cache (treats `zoom2d=2000` ItemLoader default as unset).
2. Writes them through `normalizeIconAngle` (`& 2047`) and `normalizeIconOffset` (±128) in
   `ImportOsrsItemBatch.java` — required because RS3 raw angles crash or mis-render in the 2009 client
   (e.g. Chaotic staff xan 2303 → 255).
3. Renders contact-sheet previews via `RenderClientItemIconSheet` into `tools\rs3-import\previews\`.
4. Applies per-item `iconZoom` / `iconXan` / `iconYan` / `iconZan` / `iconXoff` / `iconYoff`
   manifest overrides where RS3 raw values still look wrong after preview QA.

Re-run: `python tools/rs3_import_pipeline.py run`

## Known limits

- **OpenRS2 cache era vs wiki ids:** many RS3 OpenRS2 dumps (e.g. build 599 cache 736) use
  a much smaller item id space (max ~1800) than current wiki ids (18349+). Extraction resolves
  by **item name** when id lookup fails — the item must actually exist in that cache revision.
- **Cache era:** Chaotic weapons and Korasi are in OpenRS2 cache **554** (not 736/1475).
  Item defs live in idx=19 archives (Drygore/Goliath/Zaryte) or idx=2 arch=26 (Chaotic/Korasi).
  Extraction uses Displee cache access + RS3-aware pattern decoder (`ExtractRs3ItemDefs.java`).
- RS3 index metadata uses extra flags (checksums=8, whirlpool=2); `IndexData.java` is patched
  in the bundled runelite cache jar to parse these.

- RS3 textured models are flattened to representative colours (same as OSRS imports).
- **RS3 worn models** use a different skeleton from 2009/OSRS. The importer always
  nearest-bone rigs RS3 worn geometry against native slot references (never native RS3
  vskin). Oversized RS3 coordinates are scaled to the 2009 model range. Items whose
  worn geometry still cannot decode cleanly can set `"wornTemplate": <nativeItemId>` in
  the manifest to borrow that item's worn models while keeping the RS3 inventory icon.
- Ranged/magic weapons need additional server wiring to actually fire.
- Stats come from `rs3_items_stats.json`, not osrsreboxed.

