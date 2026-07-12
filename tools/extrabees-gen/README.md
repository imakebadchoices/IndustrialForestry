# Extra Bees datapack generator

One-shot translator: parses the Binnie **Extra Bees** 1.12 source (`../Binnie/extrabees`) and emits a
ForestryCE 1.21 datapack (namespace `extrabees`) that plugs into the datapack knobs in ForestryCE core.

The Binnie enums are 1.12 Forge Java and can't be reflected on the 1.21 classpath, so this parses the enum
**source text**. It's throwaway tooling (lives outside the jar).

## Run

```
python3 tools/extrabees-gen/generate.py      # from repo root
```

- Output datapack: `tools/extrabees-gen/pack/` (drop into a world's `datapacks/`).
- Item manifest: `tools/extrabees-gen/items-needed.json` — every `extrabees:*` item the thin module must
  register (centrifuge outputs: dusts / shards / propolis / honey-drop / modded-fallback items).
- `mappings.py` holds the confirmed old→new translation tables (allele ids, chromosome ids, colors).

## What it emits

`bee_species` (flattened full genome), `bee_mutation` (+ biome conditions), `taxon`, `flower_type`,
`comb_type`, `bee_effect`, and `recipe/centrifuge`.

## Known assumptions / deferrals (verify or revisit in-client)

- **Colors (tunable):** `outline` = Extra Bees primary color, `body` = secondary. Flip in
  `emit_species_and_mutations` if the bees look wrong.
- **Fertility scale (tunable):** old `LOW/NORMAL/HIGH/MAXIMUM` → `FERTILITY_2/3/4/4` to sit on ForestryCE's
  shifted scale (base Forest = `FERTILITY_3`). Edit `FERTILITY` in `mappings.py`.
- **Base-branch species (~11):** species on *base Forestry* branches (`AGRARIAN`/`BOGGY`/`FROZEN`/`FESTIVE`/
  `AUSTERE`) don't get those branches' allele defaults (we only have the Extra Bees branch templates). They
  get the Extra Bees default template + their own `setAlleles`. Hardcode the base branch templates to improve.
- **`FRUIT` / `MYSTICAL` flowers deferred** (need bespoke code, not a block predicate — per KNOB 2). Species
  referencing them lose the flower allele (skip-and-log) until those are built.
- **`VanillaComb.QUARTZ`** has no base ForestryCE comb → falls back to `forestry:bee_comb_honey` (3 species).
- **`JADED` mutation** has a custom `addMutationCondition` that's skipped (mutation still emitted, ungated).
- **Centrifuge outputs needing new items** reference `extrabees:*` ids from the manifest — those recipes stay
  broken until the thin item module registers them (the agreed "generate now, module next" plan). Some
  manifest ids are raw ore-dict names (`extrabees:dustsmalliron`, `extrabees:itemharz`) — curate when building
  the module.
- **One species (~`MYSTICAL`)** copies base `NOBLE`'s products programmatically (`entry.getKey()`); its
  products aren't emitted — add manually.
