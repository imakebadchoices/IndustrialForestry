# Forestry: CE Wiki

A single self-contained site — prose pages, generated genetics tables, and an
interactive breeding-tree explorer — built from the mod's own data. Genetics
content is **datamap-aware**: it comes from the live registry (via datagen) plus
the on-disk datapacks, not from parsing Java source.

## Quick build

```
pip install -r requirements.txt      # just 'markdown'
./generate.sh                         # datagen + single build step -> wiki/site
```

`generate.sh` runs datagen then `build.py`. Use `--skip-datagen` to reuse the last
species dump, and `--out DIR` to change the output.

Open `wiki/site/index.html`; `wiki/site/explorer.html` is the breeding explorer.

## How it works

Species live in two homes, unified by `scripts/species_data.py`:

1. **Code-registered species** — base bees, all trees, all butterflies. The datagen
   provider `ForestrySpeciesDumpProvider` walks the *live registry* and writes
   `build/wiki-data/species-dump.json`. No Java parsing; always in sync with the game.
2. **Datapack species** — Extra Bees today, any datapack tomorrow. Read from
   `src/main/resources/data/<ns>/forestry/bee_species` (+ `bee_mutation`), names from
   that pack's `lang`.

`build.py` is the **single build step**. It:

- renders each markdown page to a standalone HTML file (CSS inlined);
- generates, for the bees / trees / butterflies pages, an appendix — a full species
  table (grouped by genus, with climate, products and notable traits) and a mutation
  table — plus a link into the explorer;
- bundles `explorer.html`: the breeding explorer with its data inlined and **Mermaid
  fetched at build time** at a pinned version (`MERMAID_VERSION` in `build.py`) from the
  npm CDN, cached under `build/wiki-data/`. No Mermaid copy is committed to the repo.

As trees/butterflies migrate to datapacks they move from source (1) to source (2) —
nothing else changes.

## Layout

```
wiki/
├─ generate.sh              datagen + build (one command)
├─ requirements.txt         python deps (markdown)
├─ build.py                 THE build step: markdown + tables + explorer -> site/
├─ scripts/species_data.py  unified species model (registry dump + datapacks)
├─ wiki-markdown/           SOURCE prose pages (edit these; no data tables)
├─ explorer/
│  ├─ breeding-explorer-app.js   explorer logic (edit this)
│  └─ template.html              explorer page shell
└─ site/                    GENERATED output (git-ignored)
```

Edit sources — `wiki-markdown/*.md`, `explorer/breeding-explorer-app.js`,
`explorer/template.html`, and the tables in `build.py` (`SIDEBAR`, `PAGE_TITLES`,
`STYLESHEET`, `GENUS_LABELS` in `species_data.py`). Everything in `site/` is generated.

## CI

`.github/workflows/wiki.yml` runs datagen + `build.py` (JDK 21 + Python) and uploads
`wiki/site` as an artifact. The species snapshot and Mermaid are fetched/built there
and never committed.
