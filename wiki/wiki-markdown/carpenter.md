# Carpenter

The **Carpenter** is Forestry's workhorse machine — an automatic crafting table with a fixed set of recipes, most of which need a **liquid** as well as solid ingredients. Where an ordinary crafting table can't be automated, the Carpenter can, so it becomes the hub that produces most of the mod's intermediate components.

## What it makes

The Carpenter is responsible for a large slice of Forestry's recipe tree, including:

- **Soils** — Bog Earth and Humus, the specialty dirts the [farms](farming.md) and peat bogs rely on.
- **Crating and uncrating** — packing items into [crates](storage.md) for compact storage, and unpacking them again.
- **Tools and kit** — the Beealyzer and analysers, the woven [backpacks](storage.md), circuit boards, and many other components used across the mod (and by add-ons).

## How it works

The Carpenter has four working areas: a **crafting grid** where you set the recipe, a **liquid tank**, a **resource inventory** that feeds the grid, and the **output**. A recipe only runs when both its solid ingredients are present in the resource inventory *and* its required liquid (water, seed oil, honey, and so on) is in the tank.

The tank is filled by placing a filled container in its input slot or by piping fluid in, and can be emptied again with a **Pipette**. Like the other factory machines, the Carpenter runs on **Forge Energy (FE)** supplied by the [engines](energy.md) or any FE source.

See also the [Thermionic Fabricator](fabricator.md) for glass and electron tubes, and the [Squeezer](squeezer.md) for producing the seed oil many Carpenter recipes need.
